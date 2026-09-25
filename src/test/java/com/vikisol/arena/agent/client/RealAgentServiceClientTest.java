package com.vikisol.arena.agent.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// M6 (JennySol integration) - covers the deterministic, non-network parts of
// RealAgentServiceClient (availability gating, the "never call the network when not configured"
// guard). The actual HTTP call to a live JennySol gateway is NOT independently verified here -
// same "not independently verified against a live account" status Msg91PhoneOtpProvider/
// ResendEmailProvider have for the same reason (no live target configured in this environment).
class RealAgentServiceClientTest {

    private static final String SECRET = "arena-jennysol-shared-test-secret-do-not-use-in-production";

    @Test
    void isNotAvailableWhenGatewayUrlIsBlank() {
        AgentServiceTokenIssuer issuer = new AgentServiceTokenIssuer(SECRET);
        RealAgentServiceClient client = new RealAgentServiceClient("", issuer);

        assertThat(client.isAvailable()).isFalse();
    }

    @Test
    void isNotAvailableWhenGatewayUrlIsNull() {
        AgentServiceTokenIssuer issuer = new AgentServiceTokenIssuer(SECRET);
        RealAgentServiceClient client = new RealAgentServiceClient(null, issuer);

        assertThat(client.isAvailable()).isFalse();
    }

    @Test
    void isNotAvailableWhenTokenIssuerHasNoSecretConfigured() {
        AgentServiceTokenIssuer unconfiguredIssuer = new AgentServiceTokenIssuer("");
        RealAgentServiceClient client = new RealAgentServiceClient("https://api.jennysol.vikisol.in", unconfiguredIssuer);

        assertThat(client.isAvailable()).isFalse();
    }

    @Test
    void isAvailableOnlyWhenBothGatewayUrlAndTokenSecretAreConfigured() {
        AgentServiceTokenIssuer issuer = new AgentServiceTokenIssuer(SECRET);
        RealAgentServiceClient client = new RealAgentServiceClient("https://api.jennysol.vikisol.in", issuer);

        assertThat(client.isAvailable()).isTrue();
    }

    @Test
    void refusesToAttemptANetworkCallWhenNotAvailable() {
        AgentServiceTokenIssuer unconfiguredIssuer = new AgentServiceTokenIssuer("");
        RealAgentServiceClient client = new RealAgentServiceClient("", unconfiguredIssuer);

        assertThatThrownBy(() ->
                client.sendMessage(new AgentContext(java.util.UUID.randomUUID(), "TALENT"), List.of(), "hi"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("isAvailable()");
    }

    // M7 (approval-controlled write tools): a TALENT user's minted token must carry
    // arena.applyToJob so the write tool is actually offered to them through the real trigger
    // path - a real gap this test would have caught (the tool existed and was fully tested on
    // JennySol's side, but this client only ever granted arena.searchJobs until this fix).
    @Test
    void grantsApplyToJobScopeOnlyToTalentAccounts() {
        assertThat(RealAgentServiceClient.scopeFor("TALENT")).contains("arena.searchJobs", "arena.applyToJob", "arena.joinActivity", "arena.createPost", "arena.search");
    }

    @Test
    void doesNotGrantApplyToJobScopeToNonTalentAccounts() {
        assertThat(RealAgentServiceClient.scopeFor("COMPANY_ADMIN")).contains("arena.searchJobs", "arena.search").doesNotContain("arena.applyToJob", "arena.joinActivity", "arena.createPost", "arena.placeBid");
        assertThat(RealAgentServiceClient.scopeFor("RECRUITER")).contains("arena.searchJobs", "arena.search").doesNotContain("arena.applyToJob", "arena.joinActivity", "arena.createPost", "arena.placeBid");
        assertThat(RealAgentServiceClient.scopeFor("PLATFORM_ADMIN")).contains("arena.searchJobs", "arena.search").doesNotContain("arena.applyToJob", "arena.joinActivity", "arena.createPost", "arena.placeBid");
    }
}
