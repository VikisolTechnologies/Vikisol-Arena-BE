package com.vikisol.arena.agent.client;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// M7 (approval-controlled write tools) - proves the round-trip: a token minted by
// AgentServiceTokenIssuer verifies correctly through AgentServiceTokenVerifier using the same
// shared secret, and a tampered/wrong-secret/wrong-audience token is rejected. This is Arena
// verifying its OWN previously-issued token coming back on a write-tool call - see both classes'
// doc comments for the full round-trip design.
class AgentServiceTokenVerifierTest {

    private static final String SECRET = "arena-jennysol-shared-test-secret-do-not-use-in-production";

    @Test
    void verifiesATokenItsOwnIssuerMinted() {
        AgentServiceTokenIssuer issuer = new AgentServiceTokenIssuer(SECRET);
        AgentServiceTokenVerifier verifier = new AgentServiceTokenVerifier(SECRET);
        UUID userId = UUID.randomUUID();

        String token = issuer.issue(userId.toString(), "TALENT", null, List.of("arena.applyToJob"));
        AgentServiceTokenVerifier.VerifiedClaims claims = verifier.verify(token);

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.role()).isEqualTo("TALENT");
        assertThat(claims.scope()).containsExactly("arena.applyToJob");
    }

    @Test
    void rejectsATokenSignedWithADifferentSecret() {
        AgentServiceTokenIssuer wrongIssuer = new AgentServiceTokenIssuer("a-completely-different-secret-nobody-shares");
        AgentServiceTokenVerifier verifier = new AgentServiceTokenVerifier(SECRET);

        String token = wrongIssuer.issue(UUID.randomUUID().toString(), "TALENT", null, List.of());

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(AgentServiceTokenVerifier.AgentServiceTokenInvalidException.class);
    }

    @Test
    void rejectsAMalformedToken() {
        AgentServiceTokenVerifier verifier = new AgentServiceTokenVerifier(SECRET);

        assertThatThrownBy(() -> verifier.verify("not-a-real-token"))
                .isInstanceOf(AgentServiceTokenVerifier.AgentServiceTokenInvalidException.class);
    }

    @Test
    void isNotConfiguredWithoutASecretAndRefusesToVerify() {
        AgentServiceTokenVerifier unconfigured = new AgentServiceTokenVerifier("");

        assertThat(unconfigured.isConfigured()).isFalse();
        assertThatThrownBy(() -> unconfigured.verify("anything"))
                .isInstanceOf(AgentServiceTokenVerifier.AgentServiceTokenInvalidException.class);
    }

    // M10 (security testing) - a dedicated adversarial test, not previously exercised anywhere in
    // this codebase: a token signed with a DIFFERENT algorithm (HS384) than the verifier expects,
    // using the exact correct secret bytes - the same class of algorithm-upgrade footgun M5 found
    // on the ISSUING side (jjwt's bare signWith(key) silently choosing HS384 for a long enough
    // key). Tested here from the VERIFYING side: even with the real secret, a token whose header
    // claims a different HMAC variant must still be rejected, not silently accepted because the
    // key material happens to be compatible with multiple algorithms. (HS512 was tried first and
    // rejected by jjwt itself at sign time - SECRET's 464 bits clears HS384's >=384-bit floor but
    // not HS512's >=512-bit one - which is itself a real, useful confirmation that jjwt's own
    // weak-key guard is active in this exact dependency version.)
    @Test
    void rejectsATokenSignedWithADifferentAlgorithmEvenUsingTheExactCorrectSecret() {
        AgentServiceTokenVerifier verifier = new AgentServiceTokenVerifier(SECRET);
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

        String forged = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer("arena")
                .audience().add("jennysol").and()
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .claim("role", "TALENT")
                .claim("scope", List.of("arena.applyToJob"))
                .signWith(key, Jwts.SIG.HS384)
                .compact();

        assertThatThrownBy(() -> verifier.verify(forged))
                .isInstanceOf(AgentServiceTokenVerifier.AgentServiceTokenInvalidException.class);
    }

    // M10: a classic "alg: none" unsigned-token attack, forged by hand (jjwt's own builder API
    // refuses to produce this) - a real header/payload with an EMPTY signature segment, exactly
    // what this attack sends against a parser that might not enforce a real signature check.
    @Test
    void rejectsAnAlgNoneUnsignedTokenEvenWithPerfectClaims() {
        AgentServiceTokenVerifier verifier = new AgentServiceTokenVerifier(SECRET);
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();

        String header = b64.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = b64.encodeToString(
                ("{\"sub\":\"" + UUID.randomUUID() + "\",\"iss\":\"arena\",\"aud\":\"jennysol\","
                        + "\"role\":\"PLATFORM_ADMIN\",\"scope\":[\"arena.applyToJob\"],"
                        + "\"exp\":" + (Instant.now().getEpochSecond() + 300) + "}").getBytes(StandardCharsets.UTF_8));
        String forged = header + "." + payload + ".";

        assertThatThrownBy(() -> verifier.verify(forged))
                .isInstanceOf(AgentServiceTokenVerifier.AgentServiceTokenInvalidException.class);
    }
}
