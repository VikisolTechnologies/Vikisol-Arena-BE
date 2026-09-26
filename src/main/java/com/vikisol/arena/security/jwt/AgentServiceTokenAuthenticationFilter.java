package com.vikisol.arena.security.jwt;

import com.vikisol.arena.agent.client.AgentServiceTokenVerifier;
import com.vikisol.arena.audit.AuditActions;
import com.vikisol.arena.audit.AuditService;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * M7 (approval-controlled write tools, PROJECT-PROGRESS.md milestone model): lets a JennySol
 * write-tool call act as a specific, real Arena user by forwarding the exact service token Arena
 * itself minted for that user's turn (see {@link com.vikisol.arena.agent.client.AgentServiceTokenIssuer}).
 * Verifies it with {@link AgentServiceTokenVerifier} (a different secret/signature than
 * {@link JwtAuthenticationFilter}'s own real session tokens - the two are never interchangeable),
 * looks up the real {@link com.vikisol.arena.auth.entity.User} the token names, and - critically -
 * independently re-checks that the token's own {@code scope} claim actually authorizes *this*
 * specific request's path+method, per ADR-003's "Arena tools re-derive authorization
 * independently": a token scoped only for {@code arena.searchJobs} must never authenticate a
 * {@code POST /applications} call just because its signature is otherwise valid.
 * <p>
 * Runs before {@link JwtAuthenticationFilter} in the chain. A request bearing a normal Arena
 * session JWT simply fails verification here (wrong secret) and falls through untouched;
 * {@code JwtAuthenticationFilter} then authenticates it exactly as before. The two filters never
 * both succeed for the same token, since the two token types use different secrets by design.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentServiceTokenAuthenticationFilter extends OncePerRequestFilter {

    private final AgentServiceTokenVerifier verifier;
    private final UserRepository userRepository;
    private final AuditService auditService;

    // One entry per write tool (grows as tools are added). Deliberately explicit and small rather
    // than a naming convention the request path has to match automatically: a typo'd or
    // unexpectedly-shaped Arena endpoint should fail closed (no entry found -> filter does nothing
    // -> normal auth rules apply, most likely 401/403), never accidentally grant a service token
    // more than this table says it should have. Keys are "METHOD path-pattern", where "*" matches
    // exactly one path segment (an id) - never "**".
    static final Map<String, String> ENDPOINT_TO_REQUIRED_SCOPE = Map.of(
            "POST /applications", "arena.applyToJob",
            // Arena restructure Phase 3 (Jenny) write tools.
            "POST /posts", "arena.createPost",
            "POST /posts/*/joins", "arena.joinActivity",
            "POST /marketplace/projects", "arena.createProject",
            "POST /marketplace/projects/*/bids", "arena.placeBid"
    );

    private static final org.springframework.util.AntPathMatcher PATHS = new org.springframework.util.AntPathMatcher();

    // The scope a request needs, or null if no service token may authenticate it at all.
    static String requiredScopeFor(String method, String servletPath) {
        for (var entry : ENDPOINT_TO_REQUIRED_SCOPE.entrySet()) {
            String[] key = entry.getKey().split(" ", 2);
            if (key[0].equals(method) && PATHS.match(key[1], servletPath)) return entry.getValue();
        }
        return null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = getTokenFromRequest(request);

        if (StringUtils.hasText(token) && verifier.isConfigured()) {
            AgentServiceTokenVerifier.VerifiedClaims claims = tryVerify(token);

            if (claims != null) {
                String path = requestPath(request);
                String endpointKey = request.getMethod() + " " + path;
                String requiredScope = requiredScopeFor(request.getMethod(), path);
                if (requiredScope != null && claims.scope().contains(requiredScope)) {
                    var user = userRepository.findById(claims.userId());
                    if (user.isPresent()) {
                        UserPrincipal principal = new UserPrincipal(user.get());
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        // M9: the one durable record that an AI agent (not the user directly)
                        // performed this action — tenantId is null here because the token itself
                        // doesn't carry a resource-specific tenant to attribute this to; the
                        // business service call this authorizes (e.g. ApplicationService) is
                        // where a real tenant-scoped audit entry, if any, belongs.
                        auditService.record(null, claims.userId(), AuditActions.AGENT_ACTION_AUTHORIZED, endpointKey);
                    } else {
                        log.warn("Agent service token named a user id that no longer exists: {}", claims.userId());
                    }
                } else if (requiredScope != null) {
                    log.warn("Agent service token presented for {} but its scope did not authorize it", endpointKey);
                    // A verified token for a mapped write, missing the scope that write requires,
                    // is a real denial. Stop here with 403. Falling through used to become 401
                    // from the unauthenticated entry point, which hid the scope failure.
                    auditService.record(null, claims.userId(), AuditActions.AGENT_ACTION_DENIED, endpointKey,
                            "missing required scope: " + requiredScope);
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"success\":false,\"message\":\"Access denied\"}");
                    return;
                } else {
                    log.warn("Agent service token presented for {} but its scope did not authorize it", endpointKey);
                    // No mapping means this is not one of Jenny's writes (search, nearby, and the
                    // other reads). Record the probe and let the rest of the chain decide.
                    // Forcing 403 here would break those reads, which send the same bearer.
                    auditService.record(null, claims.userId(), AuditActions.AGENT_ACTION_DENIED, endpointKey,
                            "no scope mapping for this endpoint");
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    // MockMvc leaves servletPath blank and puts the path on the request URI. A real
    // deployment puts /api/v1 on the context path and /posts on the servlet path.
    // Strip the context path so both resolve to the same key the scope table uses.
    static String requestPath(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI();
            String context = request.getContextPath();
            if (context != null && !context.isEmpty() && path.startsWith(context)) {
                path = path.substring(context.length());
            }
        }
        return path == null || path.isEmpty() ? "/" : path;
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }

    // Not a service token (or an invalid one) - returns null so the caller falls through
    // silently, letting JwtAuthenticationFilter get a normal chance at this same bearer value.
    private AgentServiceTokenVerifier.VerifiedClaims tryVerify(String token) {
        try {
            return verifier.verify(token);
        } catch (AgentServiceTokenVerifier.AgentServiceTokenInvalidException e) {
            return null;
        }
    }
}
