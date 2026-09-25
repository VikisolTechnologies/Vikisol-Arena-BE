package com.vikisol.arena.security.jwt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 3 (Jenny): which endpoints a JennySol service token can ever authenticate, and with which scope. */
class AgentScopeMappingTest {

    @Test
    void eachWriteToolReachesExactlyItsOwnEndpoint() {
        assertThat(AgentServiceTokenAuthenticationFilter.requiredScopeFor("POST", "/applications")).isEqualTo("arena.applyToJob");
        assertThat(AgentServiceTokenAuthenticationFilter.requiredScopeFor("POST", "/posts")).isEqualTo("arena.createPost");
        assertThat(AgentServiceTokenAuthenticationFilter.requiredScopeFor("POST", "/posts/3f1c/joins")).isEqualTo("arena.joinActivity");
        assertThat(AgentServiceTokenAuthenticationFilter.requiredScopeFor("POST", "/marketplace/projects")).isEqualTo("arena.createProject");
        assertThat(AgentServiceTokenAuthenticationFilter.requiredScopeFor("POST", "/marketplace/projects/9a/bids")).isEqualTo("arena.placeBid");
    }

    @Test
    void nothingElseIsReachable() {
        // Other methods on the same paths, neighbouring endpoints, and deeper paths all fail closed.
        assertThat(AgentServiceTokenAuthenticationFilter.requiredScopeFor("DELETE", "/posts/3f1c")).isNull();
        assertThat(AgentServiceTokenAuthenticationFilter.requiredScopeFor("GET", "/posts")).isNull();
        assertThat(AgentServiceTokenAuthenticationFilter.requiredScopeFor("POST", "/posts/3f1c/comments")).isNull();
        assertThat(AgentServiceTokenAuthenticationFilter.requiredScopeFor("POST", "/posts/3f1c/joins/extra")).isNull();
        assertThat(AgentServiceTokenAuthenticationFilter.requiredScopeFor("POST", "/marketplace/projects/9a/award")).isNull();
        assertThat(AgentServiceTokenAuthenticationFilter.requiredScopeFor("POST", "/communities")).isNull();
        assertThat(AgentServiceTokenAuthenticationFilter.requiredScopeFor("PUT", "/profile/me/details")).isNull();
    }
}
