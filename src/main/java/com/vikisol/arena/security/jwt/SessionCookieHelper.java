package com.vikisol.arena.security.jwt;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
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
 * different scope (Path=/, read by arena-web's same-origin middleware through the Vercel proxy;
 * host-only by default, with no Domain attribute) and different lifetime (this expires with the access
 * token, ~15 min, not 7 days). Additive: the access token is still returned in the JSON body
 * too, so nothing about the existing Bearer-token flow breaks while pages migrate one at a
 * time to the server-resolved model. See DECISIONS.md for the full cross-domain rationale.
 */
@Component
public class SessionCookieHelper {

    public static final String COOKIE_NAME = "arena_session";

    private final Environment environment;

    public SessionCookieHelper(Environment environment) {
        this.environment = environment;
    }

    @Value("${app.jwt.expiration-ms}")
    private long accessExpirationMs;

    @Value("${app.jwt.cookie-secure:}")
    private String cookieSecure;

    @Value("${app.jwt.cookie-same-site:Lax}")
    private String cookieSameSite;

    // Blank by default: the Vercel proxy makes API calls same-origin on arena.vikisol.in, so the
    // browser should store a host-only cookie. Kept configurable only for emergency migration.
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
                .secure(secureCookie())
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(maxAge);
        if (StringUtils.hasText(cookieDomain)) {
            builder.domain(cookieDomain);
        }
        return builder.build();
    }

    private boolean secureCookie() {
        if (StringUtils.hasText(cookieSecure)) {
            return Boolean.parseBoolean(cookieSecure.trim());
        }
        return !environment.acceptsProfiles(Profiles.of("local"));
    }
}
