package com.vikisol.arena.common.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * OpenAI Embeddings API (https://api.openai.com/v1/embeddings) implementation of
 * {@link EmbeddingProvider} - same {@code java.net.http.HttpClient}/Jackson style as
 * {@code ResendEmailProvider}, no new HTTP library dependency. Dormant unless
 * {@code OPENAI_API_KEY} is set (see {@link EmbeddingProviderConfig}); {@link HashingEmbeddingProvider}
 * is the active default until then.
 * <p>
 * NOT independently verified against a live OpenAI account in this session - no API key exists
 * for Arena yet. The request/response shape follows OpenAI's published API contract.
 * <p>
 * Caveat worth stating plainly: {@code text-embedding-3-small} returns 1536-dimension vectors,
 * not the 128 {@link HashingEmbeddingProvider} uses - switching providers means existing stored
 * embeddings (computed at post-creation time, see {@code PostService.create}) need re-embedding
 * before cosine similarity against new posts is meaningful again. Not a concern while this stays
 * dormant; worth a one-time backfill job when a real key is actually provisioned.
 */
@Slf4j
public class OpenAiEmbeddingProvider implements EmbeddingProvider {

    private static final String ENDPOINT = "https://api.openai.com/v1/embeddings";
    private static final int DIMENSIONS = 1536;

    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public OpenAiEmbeddingProvider(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public float[] embed(String text) {
        try {
            Map<String, Object> payload = Map.of("model", model, "input", text == null ? "" : text);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new RuntimeException("OpenAI embeddings API returned " + response.statusCode() + ": " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode values = root.at("/data/0/embedding");
            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) vector[i] = (float) values.get(i).asDouble();
            return vector;
        } catch (Exception e) {
            log.error("OpenAI embedding request failed: {}", e.getMessage());
            throw new RuntimeException("Could not compute embedding via OpenAI: " + e.getMessage(), e);
        }
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }
}
