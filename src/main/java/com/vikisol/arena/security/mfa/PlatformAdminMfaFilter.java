package com.vikisol.arena.security.mfa;

import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Platform admin API calls wait until two-factor authentication is on. Enrollment itself
 * stays on /auth/2fa and /auth/me so the account can turn it on after signing in.
 * Company admin stays optional.
 */
@Component
public class PlatformAdminMfaFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final boolean required;

    public PlatformAdminMfaFilter(UserRepository userRepository,
                                  @Value("${app.security.platform-admin-2fa-required:true}") boolean required) {
        this.userRepository = userRepository;
        this.required = required;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = requestPath(request);
        if (!required || !path.startsWith("/admin")) {
            filterChain.doFilter(request, response);
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (principal.getRole() != Role.PLATFORM_ADMIN) {
            filterChain.doFilter(request, response);
            return;
        }
        boolean enrolled = userRepository.findById(principal.getId())
                .map(user -> user.isTotpEnabled())
                .orElse(false);
        if (enrolled) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"success\":false,\"message\":\"Turn on two-factor authentication before using platform admin.\",\"data\":null}");
    }

    private static String requestPath(HttpServletRequest request) {
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
}
