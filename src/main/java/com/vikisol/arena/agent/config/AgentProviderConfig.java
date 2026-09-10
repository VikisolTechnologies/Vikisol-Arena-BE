package com.vikisol.arena.agent.config;

import com.vikisol.arena.agent.client.AgentServiceClient;
import com.vikisol.arena.agent.client.AgentServiceTokenIssuer;
import com.vikisol.arena.agent.client.NoopAgentServiceClient;
import com.vikisol.arena.agent.client.RealAgentServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Mirrors {@code integration.config.IntegrationProviderConfig}: one place that resolves which
 * concrete {@link AgentServiceClient} the rest of the app gets.
 * <p>
 * As of M6 (PROJECT-PROGRESS.md milestone model), a real implementation exists
 * ({@link RealAgentServiceClient}, calling JennySol's real agent gateway) but stays dormant
 * behind the same "isAvailable() ? real : noop" pattern the other providers use - it only
 * becomes the {@code @Primary} bean once an operator deliberately sets both
 * {@code JENNYSOL_GATEWAY_URL} and {@code SERVICE_TOKEN_SECRET_ARENA} to values JennySol's own
 * deployment is also configured with. Shipping this wiring is not the same as activating it for
 * real users - see {@link RealAgentServiceClient}'s class doc.
 */
@Configuration
public class AgentProviderConfig {

    @Value("${app.agent.jennysol-gateway-url:}")
    private String jennysolGatewayUrl;

    @Bean
    @Primary
    public AgentServiceClient agentServiceClient(NoopAgentServiceClient noop, AgentServiceTokenIssuer tokenIssuer) {
        RealAgentServiceClient real = new RealAgentServiceClient(jennysolGatewayUrl, tokenIssuer);
        return real.isAvailable() ? real : noop;
    }
}
