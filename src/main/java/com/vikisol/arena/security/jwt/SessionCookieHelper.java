package com.vikisol.arena.security.jwt;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * ARENA-MASTER-ARCHITECTURE.md PART 11/12 — HttpOnly cookie carrying the short-lived access
 * token itself, so arena-web's own Next.js server can resolve who's signed in on the very
 * first request (no client-JS "load bundle -> read localStorage -> redirect -> fetch"
 * waterfall). Deliberately separate from {@link RefreshCookieHelper}, not a rename of it:
 * different scope (Domain=.vikisol.in + Path=/, readable by arena-web's middleware across
 * the arena.vikisol.in / api-arena.vikisol.in subdomain split — the refresh cookie stays
 * narrowly scoped to Path=/api/v1/auth on the API's own host, on purpose, since nothing but
 * /auth/refresh should ever see it) and different lifetime (this expires with the access
 * token, ~15 min, not 7 days). Additive: the access token is still returned in the JSON body
 * too, so nothing about the existing Bearer-token flow breaks while pages migrate one at a
 * time to the server-resolved model. See DECISIONS.md for the full cross-domain rationale.
 */
@Component
public class SessionCookieHelper {

    public static final String COOKIE_NAME = "arena_session";

    @Value("${app.jwt.expiration-ms}")
    private long accessExpirationMs;

    @Value("${app.jwt.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${app.jwt.cookie-same-site:Lax}")
    private String cookieSameSite;

    // Blank in local dev (both apps on http://localhost at different ports - a host-only
    // cookie already crosses ports fine there); ".vikisol.in" in staging/production so the
    // cookie set by api-arena.vikisol.in is also sent to, and readable by, arena.vikisol.in.
    @Value("${app.jwt.cookie-domain:}")
    private String cookieDomain;

    public void set(HttpServletResponse response, String accessToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(accessToken, Duration.ofMillis(accessExpirationMs)).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
    }

    public String read(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (var cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private ResponseCookie build(String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(maxAge);
        if (StringUtils.hasText(cookieDomain)) {
            builder.domain(cookieDomain);
        }
        return builder.build();
    }
}
