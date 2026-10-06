package com.vikisol.arena.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class CorsOriginPolicyTest {

    @Test
    void productionDefaultHasNoBrowserCorsOrigins() {
        assertThat(CorsOriginPolicy.allowedOrigins("", new MockEnvironment())).isEmpty();
    }

    @Test
    void localProfileKeepsTheFrontendDevOrigins() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        assertThat(CorsOriginPolicy.allowedOrigins("", environment))
                .containsExactly("http://localhost:3000", "http://localhost:3001");
    }

    @Test
    void explicitCorsOriginsStillWinAndAreTrimmed() {
        assertThat(CorsOriginPolicy.allowedOrigins(" https://preview-arena.vikisol.in, http://localhost:3000 ", new MockEnvironment()))
                .containsExactly("https://preview-arena.vikisol.in", "http://localhost:3000");
    }
}
