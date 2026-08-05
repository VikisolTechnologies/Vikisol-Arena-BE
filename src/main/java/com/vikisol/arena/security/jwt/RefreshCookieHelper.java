package com.vikisol.arena.security.jwt;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * HttpOnly refresh-token cookie read/write. Secure/SameSite are environment-configurable (see
 * application.yml + DECISIONS.md): local dev runs both apps on http://localhost at different
 * ports, which browsers treat as same-site but NOT secure-context, so `Secure=true` would
 * silently drop the cookie; staging/production run over HTTPS on different Railway subdomains
 * (cross-site), which needs `SameSite=None; Secure=true` to be sent at all. Never hardcode either
 * value - see JWT_COOKIE_SECURE / JWT_COOKIE_SAME_SITE in BLOCKED.md/railway env vars.
 */
@Component
public class RefreshCookieHelper {

    public static final String COOKIE_NAME = "arena_refresh";

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Value("${app.jwt.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${app.jwt.cookie-same-site:Lax}")
    private String cookieSameSite;

    public void set(HttpServletResponse response, String token) {
        ResponseCookie cookie = build(token, Duration.ofMillis(refreshExpirationMs));
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clear(HttpServletResponse response) {
        ResponseCookie cookie = build("", Duration.ZERO);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public String read(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (var cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private ResponseCookie build(String value, Duration maxAge) {
        // context-path is /api/v1 (see application.yml) - scoping the cookie to /api/v1/auth
        // keeps it out of every other request's headers, it's only ever needed by the auth
        // endpoints that read it.
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
    }
}
