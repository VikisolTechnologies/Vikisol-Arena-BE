package com.vikisol.arena.agent.client;

import org.junit.jupiter.api.Test;

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
}
