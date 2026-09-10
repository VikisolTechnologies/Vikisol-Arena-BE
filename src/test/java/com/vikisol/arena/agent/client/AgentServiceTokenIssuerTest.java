package com.vikisol.arena.agent.client;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// M5 (JennySol integration, PROJECT-PROGRESS.md milestone model) - this is arena-api's first
// automated test file (a real, pre-existing gap confirmed during the earlier architecture
// investigation: zero test files existed anywhere in this repository). Proves
// AgentServiceTokenIssuer produces a token byte-for-byte compatible with what JennySol's
// serviceToken.ts verifier expects (same algorithm, same claim names) - the exact cross-repo
// interoperability M5 requires, verified here on the Arena side; the matching JennySol-side proof
// lives in that repository's productConnectors/arena.test.ts using a real token captured from a
// run of this test.
class AgentServiceTokenIssuerTest {

    private static final String TEST_SECRET = "arena-jennysol-shared-test-secret-do-not-use-in-production";

    @Test
    void issuesAWellFormedTokenJennySolsVerifierCanRead() {
        AgentServiceTokenIssuer issuer = new AgentServiceTokenIssuer(TEST_SECRET);

        String token = issuer.issue("arena-user-42", "RECRUITER", "tenant-1", List.of("arena.searchJobs"));

        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer("arena")
                .requireAudience("jennysol")
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("arena-user-42");
        assertThat(claims.getIssuer()).isEqualTo("arena");
        assertThat(claims.getAudience()).containsExactly("jennysol");
        assertThat(claims.get("role", String.class)).isEqualTo("RECRUITER");
        assertThat(claims.get("tenantId", String.class)).isEqualTo("tenant-1");
        assertThat(claims.get("scope", List.class)).containsExactly("arena.searchJobs");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void clampsAnOverlyLongRequestedTtlToTheMaximum() {
        AgentServiceTokenIssuer issuer = new AgentServiceTokenIssuer(TEST_SECRET);

        String token = issuer.issue("arena-user-42", "TALENT", null, List.of(), 3600); // asks for 1 hour

        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();

        long ttlSeconds = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
        assertThat(ttlSeconds).isLessThanOrEqualTo(300); // MAX_TTL_SECONDS, never the requested 3600
    }

    @Test
    void refusesToIssueWhenNoSecretIsConfigured() {
        AgentServiceTokenIssuer unconfigured = new AgentServiceTokenIssuer("");

        assertThat(unconfigured.isConfigured()).isFalse();
        assertThatThrownBy(() -> unconfigured.issue("user", "TALENT", null, List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SERVICE_TOKEN_SECRET_ARENA");
    }

    @Test
    void isConfiguredReflectsWhetherARealSecretIsSet() {
        assertThat(new AgentServiceTokenIssuer(TEST_SECRET).isConfigured()).isTrue();
        assertThat(new AgentServiceTokenIssuer(null).isConfigured()).isFalse();
        assertThat(new AgentServiceTokenIssuer("   ").isConfigured()).isFalse();
    }
}
