package com.vikisol.arena.security.jwt;

import com.vikisol.arena.auth.entity.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Security regression suite for JwtTokenProvider (Arena's own real user-session JWT - distinct
// from AgentServiceTokenIssuer/Verifier, which already had this exact bug class found and fixed
// at M10). Exercises the REAL JwtTokenProvider class directly - no mocking - the same way
// AgentServiceTokenVerifierTest exercises the real AgentServiceTokenVerifier. Constructor
// injection (see JwtTokenProvider's own comment) is what makes that possible without a Spring
// context, so this suite needs no database/Redis, same as every other test in this module.
class JwtTokenProviderTest {

    // >= 64 bytes so both HS384 (>=48 bytes) and HS512 (>=64 bytes) can actually be constructed
    // for the forgery tests below - a short test secret would make jjwt itself refuse to sign an
    // HS512 token, which would prove nothing about the verifier under test.
    private static final String SECRET =
            "arena-session-jwt-regression-test-secret-at-least-64-bytes-long-for-hs512-forging";
    private static final String ISSUER = "vikisol-arena";
    private static final String AUDIENCE = "vikisol-arena-web";
    private static final long EXPIRATION_MS = 900_000L; // 15 min, matches application.yml default

    private JwtTokenProvider provider() {
        return new JwtTokenProvider(SECRET, EXPIRATION_MS, ISSUER, AUDIENCE);
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    // ===== Intended-algorithm round trip =====

    @Test
    void issuesAndVerifiesARealSessionTokenSignedWithTheIntendedAlgorithm() {
        JwtTokenProvider tokenProvider = provider();
        UUID userId = UUID.randomUUID();

        String token = tokenProvider.generateToken(userId, "talent@example.com", "Talent User", Role.TALENT);

        assertThat(tokenProvider.validateToken(token)).isTrue();
        assertThat(tokenProvider.getEmailFromToken(token)).isEqualTo("talent@example.com");
        assertThat(tokenProvider.getRoleFromToken(token)).isEqualTo(Role.TALENT);
        assertThat(tokenProvider.isMfaPending(token)).isFalse();
    }

    @Test
    void issuesAndVerifiesARealMfaPendingToken() {
        JwtTokenProvider tokenProvider = provider();
        UUID userId = UUID.randomUUID();

        String token = tokenProvider.generateMfaPendingToken(userId);

        assertThat(tokenProvider.validateToken(token)).isTrue();
        assertThat(tokenProvider.isMfaPending(token)).isTrue();
        assertThat(tokenProvider.getUserIdFromMfaPendingToken(token)).isEqualTo(userId);
    }

    // ===== Algorithm confusion (the actual vulnerability this suite regression-tests) =====

    @Test
    void rejectsATokenSignedWithHs384EvenUsingTheExactCorrectSecret() {
        JwtTokenProvider tokenProvider = provider();
        String forged = sessionShapedToken(Role.TALENT, Jwts.SIG.HS384);

        assertThat(tokenProvider.validateToken(forged)).isFalse();
    }

    @Test
    void rejectsATokenSignedWithHs512EvenUsingTheExactCorrectSecret() {
        JwtTokenProvider tokenProvider = provider();
        String forged = sessionShapedToken(Role.TALENT, Jwts.SIG.HS512);

        assertThat(tokenProvider.validateToken(forged)).isFalse();
    }

    // A classic "alg: none" unsigned-token attack, forged by hand (jjwt's own builder API refuses
    // to produce this) - a real header/payload with an empty signature segment.
    @Test
    void rejectsAnAlgNoneUnsignedTokenEvenWithPerfectClaims() {
        JwtTokenProvider tokenProvider = provider();
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();

        String header = b64.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = b64.encodeToString(
                ("{\"sub\":\"admin@example.com\",\"iss\":\"" + ISSUER + "\",\"aud\":\"" + AUDIENCE + "\","
                        + "\"uid\":\"" + UUID.randomUUID() + "\",\"role\":\"PLATFORM_ADMIN\","
                        + "\"exp\":" + (Instant.now().getEpochSecond() + 900) + "}")
                        .getBytes(StandardCharsets.UTF_8));
        String forged = header + "." + payload + ".";

        assertThat(tokenProvider.validateToken(forged)).isFalse();
    }

    @Test
    void rejectsAMalformedAlgorithmHeader() {
        JwtTokenProvider tokenProvider = provider();
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String header = b64.encodeToString("{\"alg\":\"NOT-A-REAL-ALG\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

        assertThat(tokenProvider.validateToken(header + ".not-real-either.not-real-either")).isFalse();
    }

    @Test
    void rejectsAMalformedToken() {
        JwtTokenProvider tokenProvider = provider();

        assertThat(tokenProvider.validateToken("not-a-real-token")).isFalse();
    }

    // ===== Signature / secret =====

    @Test
    void rejectsATokenSignedWithTheWrongSecret() {
        JwtTokenProvider realProvider = provider();
        JwtTokenProvider wrongSecretProvider =
                new JwtTokenProvider("a-completely-different-secret-nobody-shares-here-ok", EXPIRATION_MS, ISSUER, AUDIENCE);

        String token = wrongSecretProvider.generateToken(UUID.randomUUID(), "eve@example.com", "Eve", Role.TALENT);

        assertThat(realProvider.validateToken(token)).isFalse();
    }

    // ===== Claim tampering: modified role / user id (payload edited, original signature kept) =====

    @Test
    void rejectsATokenWithATamperedRoleClaim() {
        JwtTokenProvider tokenProvider = provider();
        String token = tokenProvider.generateToken(UUID.randomUUID(), "talent@example.com", "Talent User", Role.TALENT);

        String tampered = spliceClaim(token, "\"role\":\"TALENT\"", "\"role\":\"PLATFORM_ADMIN\"");

        assertThat(tokenProvider.validateToken(tampered)).isFalse();
    }

    @Test
    void rejectsATokenWithATamperedUserIdClaim() {
        JwtTokenProvider tokenProvider = provider();
        UUID realUserId = UUID.randomUUID();
        UUID victimUserId = UUID.randomUUID();
        String token = tokenProvider.generateToken(realUserId, "talent@example.com", "Talent User", Role.TALENT);

        String tampered = spliceClaim(token, "\"uid\":\"" + realUserId + "\"", "\"uid\":\"" + victimUserId + "\"");

        assertThat(tokenProvider.validateToken(tampered)).isFalse();
    }

    // Note: unlike the JennySol<->Arena service token, this session token carries no tenantId
    // claim to tamper with in the first place - Arena derives tenant scope server-side from the
    // authenticated user id via Membership/EnterpriseProfileService lookups, not from a JWT claim
    // (see JwtAuthenticationFilter -> CustomUserDetailsService). The uid-tampering test above is
    // the equivalent coverage for this token shape: a forged/altered identity claim must be
    // rejected by signature verification, the same mechanism that would catch a forged tenantId
    // claim if one existed.

    // ===== Expiration =====

    @Test
    void rejectsAnExpiredToken() {
        JwtTokenProvider tokenProvider = provider();
        Instant past = Instant.now().minusSeconds(3600);
        String expired = Jwts.builder()
                .subject("talent@example.com")
                .id(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .issuedAt(Date.from(past.minusSeconds(900)))
                .notBefore(Date.from(past.minusSeconds(900)))
                .expiration(Date.from(past))
                .claim("uid", UUID.randomUUID().toString())
                .claim("role", Role.TALENT.name())
                .signWith(key(), Jwts.SIG.HS256)
                .compact();

        assertThat(tokenProvider.validateToken(expired)).isFalse();
    }

    @Test
    void rejectsATokenNotYetValid() {
        JwtTokenProvider tokenProvider = provider();
        Instant future = Instant.now().plusSeconds(3600);
        String notYetValid = Jwts.builder()
                .subject("talent@example.com")
                .id(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .issuedAt(Date.from(Instant.now().minusSeconds(5)))
                .notBefore(Date.from(future))
                .expiration(Date.from(future.plusSeconds(900)))
                .claim("uid", UUID.randomUUID().toString())
                .claim("role", Role.TALENT.name())
                .signWith(key(), Jwts.SIG.HS256)
                .compact();

        assertThat(tokenProvider.validateToken(notYetValid)).isFalse();
    }

    // ===== helpers =====

    private String sessionShapedToken(Role role, io.jsonwebtoken.security.MacAlgorithm alg) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("attacker@example.com")
                .id(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .issuedAt(Date.from(now))
                .notBefore(Date.from(now))
                .expiration(Date.from(now.plusSeconds(900)))
                .claim("uid", UUID.randomUUID().toString())
                .claim("role", role.name())
                .signWith(key(), alg)
                .compact();
    }

    /** Edits the (base64url-decoded) payload segment of a real token in place, keeping the
     * original signature - simulates an attacker intercepting and altering a claim by hand. */
    private String spliceClaim(String token, String find, String replace) {
        String[] parts = token.split("\\.");
        Base64.Decoder dec = Base64.getUrlDecoder();
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String payload = new String(dec.decode(parts[1]), StandardCharsets.UTF_8);
        String tamperedPayload = payload.replace(find, replace);
        return parts[0] + "." + enc.encodeToString(tamperedPayload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];
    }
}
