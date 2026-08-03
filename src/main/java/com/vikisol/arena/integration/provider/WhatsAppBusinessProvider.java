package com.vikisol.arena.integration.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * WhatsApp Business Platform implementation of {@link WhatsAppProvider}, targeting Meta's Cloud
 * API directly (POST {@code https://graph.facebook.com/v21.0/{phoneNumberId}/messages}) as the
 * most likely default BSP - the founder hasn't chosen one yet (Meta directly vs. a BSP like
 * Twilio/Gupshup), so this is a best-guess shape, not a confirmed integration choice. Same plain
 * {@code java.net.http.HttpClient} style as {@link ResendEmailProvider}/HRLMS-BE's
 * {@code Microsoft365Provider}.
 * <p>
 * NOT independently verified against a live WhatsApp Business account - no BSP account exists yet
 * to test against, and {@link #isConfigured()} correctly stays false with no env vars set, so this
 * code path never actually runs today. Template {@code params} are passed as positional "body"
 * component parameters ({{1}}, {{2}}, ...), which is how Meta template variables work; the actual
 * ordering will need to match whatever templates get approved once a BSP is chosen.
 * <p>
 * Deliberately NOT a {@code @Component} - see {@link ResendEmailProvider}'s javadoc for why.
 */
@Slf4j
public class WhatsAppBusinessProvider implements WhatsAppProvider {

    private final String accessToken;
    private final String phoneNumberId;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final String GRAPH_API_VERSION = "v21.0";

    public WhatsAppBusinessProvider(String accessToken, String phoneNumberId) {
        this.accessToken = accessToken;
        this.phoneNumberId = phoneNumberId;
    }

    @Override
    public String getProviderName() {
        return "WhatsApp Business (Meta Cloud API)";
    }

    @Override
    public boolean isConfigured() {
        return accessToken != null && !accessToken.isBlank() && phoneNumberId != null && !phoneNumberId.isBlank();
    }

    @Override
    public void sendMessage(String to, String templateName, Map<String, String> params) {
        if (!isConfigured()) {
            // Should be unreachable in practice - IntegrationProviderConfig only wires this bean in
            // when isConfigured() is true - but guarded explicitly since a BSP account has never
            // actually been tested against, per the brief's "throw clearly if somehow invoked".
            throw new IllegalStateException("WhatsAppBusinessProvider invoked without WHATSAPP_ACCESS_TOKEN/WHATSAPP_PHONE_NUMBER_ID configured");
        }
        try {
            List<Map<String, Object>> parameters = params == null ? List.of()
                    : params.values().stream().<Map<String, Object>>map(v -> Map.of("type", "text", "text", v)).toList();
            Map<String, Object> template = new java.util.HashMap<>(Map.of(
                    "name", templateName,
                    "language", Map.of("code", "en")));
            if (!parameters.isEmpty()) {
                template.put("components", List.of(Map.of("type", "body", "parameters", parameters)));
            }
            Map<String, Object> payload = Map.of(
                    "messaging_product", "whatsapp",
                    "to", to,
                    "type", "template",
                    "template", template);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://graph.facebook.com/" + GRAPH_API_VERSION + "/" + phoneNumberId + "/messages"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new RuntimeException("WhatsApp Cloud API returned " + response.statusCode() + ": " + response.body());
            }
            log.info("WhatsApp template \"{}\" sent to {}", templateName, to);
        } catch (Exception e) {
            log.error("WhatsApp send failed: {}", e.getMessage());
            throw new RuntimeException("Could not send WhatsApp message: " + e.getMessage(), e);
        }
    }
}
