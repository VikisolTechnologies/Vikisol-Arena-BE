package com.vikisol.arena.integration.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Resend (https://resend.com) HTTP API implementation of {@link EmailProvider} - a plain
 * POST to {@code https://api.resend.com/emails} with a Bearer API key, using
 * {@code java.net.http.HttpClient} (no new HTTP library dependency), the same style HRLMS-BE's
 * {@code Microsoft365Provider}/{@code EmailService} use for their own outbound HTTP calls.
 * <p>
 * NOT independently verified against a live Resend account in this session - no API key exists
 * for Arena yet (the founder hasn't provisioned one). The request/response shape follows Resend's
 * published API contract, but this should be smoke-tested with {@code sendTestEmail}-style manual
 * verification against a real Resend account before being relied on in production.
 * <p>
 * Deliberately NOT a {@code @Component} - constructed by
 * {@link com.vikisol.arena.integration.config.IntegrationProviderConfig} from env-var-backed
 * config, same reasoning as HRLMS-BE's {@code Microsoft365Provider} not being a Spring bean either:
 * avoids any ambiguity about which {@link EmailProvider} bean wins when no key is configured.
 */
@Slf4j
public class ResendEmailProvider implements EmailProvider {

    private final String apiKey;
    private final String fromAddress;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final String RESEND_ENDPOINT = "https://api.resend.com/emails";

    public ResendEmailProvider(String apiKey, String fromAddress) {
        this.apiKey = apiKey;
        this.fromAddress = fromAddress;
    }

    @Override
    public String getProviderName() {
        return "Resend";
    }

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public void sendEmail(EmailMessage message) {
        try {
            Map<String, Object> payload = Map.of(
                    "from", fromAddress,
                    "to", message.to(),
                    "subject", message.subject(),
                    "html", message.htmlBody() != null ? message.htmlBody() : "");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RESEND_ENDPOINT))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new RuntimeException("Resend API returned " + response.statusCode() + ": " + response.body());
            }
            log.info("Email sent via Resend to {}: {}", message.to(), message.subject());
        } catch (Exception e) {
            log.error("Resend email send failed: {}", e.getMessage());
            throw new RuntimeException("Could not send email via Resend: " + e.getMessage(), e);
        }
    }
}
