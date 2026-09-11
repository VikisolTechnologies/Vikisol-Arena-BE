package com.vikisol.arena.security.jwt;

import com.vikisol.arena.auth.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

// Constructor-injected (not @PostConstruct-initialized) so a test can construct one directly
// with a known secret without a Spring context - same reason AgentServiceTokenIssuer/Verifier
// are constructor-injected (see those classes' doc comments). This is what let this class's own
// algorithm-confusion regression tests (JwtTokenProviderTest) exercise the real class instead of
// mocking it.
@Slf4j
@Component
public class JwtTokenProvider {

    private final String issuer;
    private final String audience;
    private final long jwtExpirationMs;
    private final SecretKey key;

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_NAME = "name";
    // Set on a short-lived pre-auth token issued after a correct password but before a required
    // TOTP code is verified (see AuthService's 2FA flow). Never carries real session authority -
    // JwtAuthenticationFilter treats a token with this claim as unauthenticated.
    private static final String CLAIM_MFA_PENDING = "mfa_pending";
    // Same algorithm-confusion class already found and fixed in AgentServiceTokenVerifier (M10,
    // see that class's own comment): Jwts.parser().verifyWith(SecretKey) accepts ANY HMAC-SHA
    // variant that validates against the key's bytes, not only the one the issuer actually used.
    // Confirmed exploitable here too - the local-dev fallback secret alone is 76 bytes, long
    // enough for jjwt's bare signWith(key) to auto-select HS512 instead of the intended HS256.
    // Pin explicitly on both the signing side (signWith(key, Jwts.SIG.HS256)) and the verifying
    // side (this constant, checked against the parsed token's actual header) - do not infer an
    // acceptable algorithm solely from the token header.
    private static final String EXPECTED_ALGORITHM = "HS256";

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.jwt.expiration-ms}") long jwtExpirationMs,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.audience}") String audience) {
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.jwtExpirationMs = jwtExpirationMs;
        this.issuer = issuer;
        this.audience = audience;
    }

    public String generateToken(UUID userId, String email, String name, Role role) {
        return build(email, jwtExpirationMs)
                .claim(CLAIM_USER_ID, userId.toString())
                .claim(CLAIM_NAME, name)
                .claim(CLAIM_ROLE, role.name())
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    // Issued after password check, before TOTP verification (see DECISIONS.md's 2FA flow) - 2
    // min lifetime, carries no role/uid claims a resource server would honor, so even if
    // JwtAuthenticationFilter had a bug it grants nothing.
    public String generateMfaPendingToken(UUID userId) {
        return build(userId.toString(), 120_000L)
                .claim(CLAIM_MFA_PENDING, true)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private JwtBuilder build(String subject, long ttlMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ttlMs);
        return Jwts.builder()
                .subject(subject)
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(now)
                .notBefore(now)
                .expiration(expiry);
    }

    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public Role getRoleFromToken(String token) {
        return Role.valueOf(parseClaims(token).get(CLAIM_ROLE, String.class));
    }

    public String getJtiFromToken(String token) {
        return parseClaims(token).getId();
    }

    public Instant getExpiryFromToken(String token) {
        return parseClaims(token).getExpiration().toInstant();
    }

    public boolean isMfaPending(String token) {
        return Boolean.TRUE.equals(parseClaims(token).get(CLAIM_MFA_PENDING, Boolean.class));
    }

    public UUID getUserIdFromMfaPendingToken(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    private Claims parseClaims(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token);

        String actualAlgorithm = jws.getHeader().getAlgorithm();
        if (!EXPECTED_ALGORITHM.equals(actualAlgorithm)) {
            throw new JwtException("Unexpected signing algorithm: " + actualAlgorithm);
        }

        return jws.getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Invalid JWT token: {}", ex.getMessage());
            return false;
        }
    }
}
