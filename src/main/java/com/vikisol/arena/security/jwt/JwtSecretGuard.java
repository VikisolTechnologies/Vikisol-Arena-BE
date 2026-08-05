package com.vikisol.arena.security.jwt;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Fails startup loudly instead of silently signing production JWTs with the checked-in dev
// fallback secret (checklist §1's "rotate every credential that ever touched dev/localhost").
// JWT_REQUIRE_REAL_SECRET is set true only in staging/production env vars (see railway.toml) -
// left false for local dev so the fallback keeps working there.
@Component
public class JwtSecretGuard {

    private static final String DEV_FALLBACK =
            "local-dev-only-arena-secret-do-not-use-in-any-deployed-environment-change-me";

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.require-real-secret:false}")
    private boolean requireRealSecret;

    @PostConstruct
    public void verify() {
        if (requireRealSecret && DEV_FALLBACK.equals(jwtSecret)) {
            throw new IllegalStateException(
                    "JWT_REQUIRE_REAL_SECRET is true but JWT_SECRET is still the dev fallback - "
                            + "refusing to start with a known, publicly-committed signing key.");
        }
        if (requireRealSecret && jwtSecret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters for HS256 - refusing to start.");
        }
    }
}
