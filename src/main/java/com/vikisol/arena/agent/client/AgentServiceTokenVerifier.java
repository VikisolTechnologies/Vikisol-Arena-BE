package com.vikisol.arena.agent.client;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * M7 (approval-controlled write tools, PROJECT-PROGRESS.md milestone model): verifies a service
 * token Arena itself minted (via {@link AgentServiceTokenIssuer}) when it comes back on a
 * JennySol-originated write-tool call. Round-trip design, not a new credential type: JennySol's
 * {@code arena.applyToJob} tool forwards the exact same bearer token Arena gave it for this turn
 * - Arena is simply re-verifying its own signature, using the same shared secret, to confirm the
 * request really is acting on behalf of the user it originally vouched for.
 * <p>
 * Deliberately separate from {@code JwtTokenProvider} (Arena's own session-JWT
 * verifier) - this only ever accepts tokens signed with {@code app.agent.service-token-secret},
 * never {@code app.jwt.secret}, so a normal Arena session token is never mistakenly accepted here
 * and a service token is never mistakenly accepted as a real session token elsewhere.
 */
@Component
public class AgentServiceTokenVerifier {

    private static final String EXPECTED_ISSUER = "arena";
    private static final String EXPECTED_AUDIENCE = "jennysol";
    // M10 (security testing, PROJECT-PROGRESS.md milestone model): a real algorithm-confusion gap
    // found by a dedicated adversarial test, not previously exercised anywhere in this codebase -
    // Jwts.parser().verifyWith(SecretKey) accepts ANY HMAC-SHA variant whose signature validates
    // against that key's bytes (HS256, HS384, and - for a long enough key - HS512), not only the
    // exact algorithm the issuer actually used. A token forged with HS384 (using the real secret,
    // no forgery of the secret itself needed) verified successfully before this fix. The verifier
    // must pin to exactly one algorithm, the same way serviceToken.ts's own
    // jwt.verify(token, secret, {algorithms: ["HS256"]}) already does on the JennySol side.
    private static final String EXPECTED_ALGORITHM = "HS256";

    private final String secret;

    public AgentServiceTokenVerifier(@Value("${app.agent.service-token-secret:}") String secret) {
        this.secret = secret;
    }

    public boolean isConfigured() {
        return secret != null && !secret.isBlank();
    }

    public record VerifiedClaims(UUID userId, String role, List<String> scope) {}

    /**
     * @throws AgentServiceTokenInvalidException if the token is missing, malformed, expired, has
     *                                            the wrong issuer/audience, or fails signature
     *                                            verification. Callers must never treat a caught
     *                                            exception as "authenticate as anonymous" - the
     *                                            correct response is to reject the request.
     */
    public VerifiedClaims verify(String token) {
        if (!isConfigured()) {
            throw new AgentServiceTokenInvalidException("Agent service token verification is not configured");
        }
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(EXPECTED_ISSUER)
                    .requireAudience(EXPECTED_AUDIENCE)
                    .build()
                    .parseSignedClaims(token);

            String actualAlgorithm = jws.getHeader().getAlgorithm();
            if (!EXPECTED_ALGORITHM.equals(actualAlgorithm)) {
                throw new AgentServiceTokenInvalidException("Unexpected signing algorithm: " + actualAlgorithm);
            }

            Claims claims = jws.getPayload();
            UUID userId = UUID.fromString(claims.getSubject());
            String role = claims.get("role", String.class);
            @SuppressWarnings("unchecked")
            List<String> scope = claims.get("scope", List.class);
            return new VerifiedClaims(userId, role, scope != null ? scope : List.of());
        } catch (JwtException | IllegalArgumentException e) {
            throw new AgentServiceTokenInvalidException("Invalid agent service token: " + e.getMessage(), e);
        }
    }

    public static class AgentServiceTokenInvalidException extends RuntimeException {
        public AgentServiceTokenInvalidException(String message) {
            super(message);
        }

        public AgentServiceTokenInvalidException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
