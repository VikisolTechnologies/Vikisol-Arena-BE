package com.vikisol.arena.common.embedding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Same resolve-real-or-fall-back-to-Noop shape as
 * {@code integration.config.IntegrationProviderConfig} - callers only ever depend on
 * {@link EmbeddingProvider}, never construct {@link OpenAiEmbeddingProvider}/
 * {@link HashingEmbeddingProvider} directly.
 */
@Configuration
public class EmbeddingProviderConfig {

    @Value("${openai.api-key:}")
    private String openAiApiKey;

    @Value("${openai.embedding-model:text-embedding-3-small}")
    private String openAiEmbeddingModel;

    @Bean
    @Primary
    public EmbeddingProvider embeddingProvider(HashingEmbeddingProvider hashingEmbeddingProvider) {
        OpenAiEmbeddingProvider real = new OpenAiEmbeddingProvider(openAiApiKey, openAiEmbeddingModel);
        return real.isConfigured() ? real : hashingEmbeddingProvider;
    }
}
