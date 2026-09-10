package com.vikisol.arena.agent.client;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

/**
 * Mints the short-lived, scoped service token that lets an authenticated Arena user's own
 * request act through JennySol - see JennySol repository's docs/architecture/ADR-003
 * (service-token-security) for the full design, and its server/src/services/serviceToken.ts for
 * the verifier this must stay byte-for-byte compatible with (same HS256 algorithm, same claim
 * names: sub/iss/aud/role/tenantId/scope/exp).
 * <p>
 * Deliberately a separate key, secret, and audience from {@code JwtTokenProvider} (Arena's own
 * session-JWT issuer) - a service token must never be confusable with, or independently
 * verifiable as, a real Arena session token, and vice versa. This class only ever issues; nothing
 * in Arena verifies a service token, since Arena is never the one receiving one - it's minted
 * here and handed to JennySol, which verifies it against the same shared secret on its side.
 * <p>
 * Constructor-injected (not {@code @PostConstruct}-initialized like {@code JwtTokenProvider})
 * specifically so a test can construct one directly with a known secret without a Spring context -
 * see {@code AgentServiceTokenIssuerTest}.
 */
@Component
public class AgentServiceTokenIssuer {

    private static final String ISSUER = "arena";
    private static final String AUDIENCE = "jennysol";
    // Mirrors serviceToken.ts's MAX_TOKEN_TTL_SECONDS exactly - ADR-003: "a stolen token is only
    // useful for minutes, not for the life of a login session."
    private static final int MAX_TTL_SECONDS = 300;

    private final String secret;

    public AgentServiceTokenIssuer(@Value("${app.agent.service-token-secret:}") String secret) {
        this.secret = secret;
    }

    public boolean isConfigured() {
        return secret != null && !secret.isBlank();
    }

    public String issue(String externalUserId, String role, String tenantId, List<String> scope) {
        return issue(externalUserId, role, tenantId, scope, MAX_TTL_SECONDS);
    }

    public String issue(String externalUserId, String role, String tenantId, List<String> scope, int ttlSeconds) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "app.agent.service-token-secret (SERVICE_TOKEN_SECRET_ARENA) is not configured - cannot mint a JennySol service token");
        }
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        int clampedTtl = Math.min(ttlSeconds, MAX_TTL_SECONDS);

        return Jwts.builder()
                .subject(externalUserId)
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .claim("role", role)
                .claim("tenantId", tenantId)
                .claim("scope", scope)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(clampedTtl, ChronoUnit.SECONDS)))
                // Explicit HS256, not the bare signWith(key) overload - jjwt auto-upgrades that
                // to HS384/HS512 when the key is long enough, which silently breaks
                // interoperability with JennySol's serviceToken.ts verifier (it restricts
                // jwt.verify()'s `algorithms` allowlist to exactly ["HS256"]). Caught by actually
                // running this issuer's real output through that real verifier during M5, not by
                // unit-testing either side in isolation - see PROJECT-PROGRESS.md's M5 entry.
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
