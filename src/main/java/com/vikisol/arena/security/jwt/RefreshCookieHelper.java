package com.vikisol.arena.security.jwt;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * HttpOnly refresh-token cookie read/write. Secure/SameSite are environment-configurable (see
 * application.yml + DECISIONS.md). The Vercel proxy makes app traffic same-origin, so production
 * defaults to `SameSite=Lax; Secure` and no Domain attribute. Local HTTP keeps working because the
 * secure default is profile-aware, and both values remain overrideable for tests/emergencies.
 */
@Component
public class RefreshCookieHelper {

    public static final String COOKIE_NAME = "arena_refresh";

    private final Environment environment;

    public RefreshCookieHelper(Environment environment) {
        this.environment = environment;
    }

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Value("${app.jwt.cookie-secure:}")
    private String cookieSecure;

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
                .secure(secureCookie())
                .sameSite(cookieSameSite)
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
    }

    private boolean secureCookie() {
        if (StringUtils.hasText(cookieSecure)) {
            return Boolean.parseBoolean(cookieSecure.trim());
        }
        return !environment.acceptsProfiles(Profiles.of("local"));
    }
}
