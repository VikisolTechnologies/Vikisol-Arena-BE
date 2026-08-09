package com.vikisol.arena.security.jwt;

import com.vikisol.arena.security.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Bearer-token auth (Authorization: Bearer <token>) remains the primary path for the SPA's
// own client-side fetches. ARENA-MASTER-ARCHITECTURE.md PART 11 adds a second path: the
// HttpOnly `arena_session` cookie (see SessionCookieHelper), read only when no Authorization
// header is present - this lets arena-web's Next.js server (middleware, server components)
// forward the cookie straight through on server-to-server calls without needing to first
// unwrap it into a header itself. The refresh token is a separate, narrower-scoped cookie
// (see RefreshCookieHelper) that never reaches this filter - /auth/refresh reads it directly.
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final TokenDenylistService tokenDenylistService;
    private final SessionCookieHelper sessionCookieHelper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = getTokenFromRequest(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token) && !jwtTokenProvider.isMfaPending(token)) {
            String jti = jwtTokenProvider.getJtiFromToken(token);
            if (!tokenDenylistService.isDenylisted(jti)) {
                String email = jwtTokenProvider.getEmailFromToken(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return sessionCookieHelper.read(request);
    }
}
