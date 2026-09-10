package com.vikisol.arena.agent.config;

import com.vikisol.arena.agent.client.AgentServiceClient;
import com.vikisol.arena.agent.client.NoopAgentServiceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Mirrors {@code integration.config.IntegrationProviderConfig}: one place that resolves which
 * concrete {@link AgentServiceClient} the rest of the app gets. There is only the Noop binding
 * today - see {@link AgentServiceClient}'s class doc for the full reasoning. When a real agent
 * backend is ready to integrate, its client bean is added here behind the same
 * "isConfigured() ? real : noop" pattern the other providers use (e.g. an `agent.service.url`
 * property), and nothing outside this file needs to change.
 */
@Configuration
public class AgentProviderConfig {

    @Bean
    @Primary
    public AgentServiceClient agentServiceClient(NoopAgentServiceClient noop) {
        return noop;
    }
}
