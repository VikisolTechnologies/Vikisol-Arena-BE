package com.vikisol.arena.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Verifies a Google Identity Services ID token (the credential the frontend's "Sign in with
 * Google" button hands back) by calling Google's own {@code tokeninfo} endpoint rather than
 * fetching Google's JWKS and checking the RS256 signature ourselves - same
 * "raw java.net.http, no new HTTP/crypto library" style as {@code OpenAiEmbeddingProvider}/
 * {@code ResendEmailProvider}. Google's own docs note this endpoint isn't recommended for
 * very-high-volume verification (rate limits), suggesting a client library with local JWKS
 * caching instead once traffic actually justifies it - at Arena's current scale, one call per
 * sign-in is a reasonable, honest trade rather than adding a whole new dependency (google-api-
 * client pulls in Guava/google-http-client/gson) for a still-early product.
 * <p>
 * Dormant unless {@code GOOGLE_CLIENT_ID} is set - {@link #isConfigured()} gates every call site
 * (AuthService.signInWithGoogle), same "isConfigured()" contract as PhoneOtpProvider/EmailProvider.
 */
@Slf4j
@Component
public class GoogleIdTokenVerifier {

    private static final String TOKENINFO_ENDPOINT = "https://oauth2.googleapis.com/tokeninfo";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${google.client-id:}")
    private String clientId;

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank();
    }

    /** Returns the verified identity, or null if the token is invalid/expired/wrong audience. */
    public Verified verify(String idToken) {
        if (!isConfigured()) {
            throw new IllegalStateException("Google sign-in isn't configured (GOOGLE_CLIENT_ID unset)");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TOKENINFO_ENDPOINT + "?id_token=" + idToken))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Google tokeninfo rejected the token: {} {}", response.statusCode(), response.body());
                return null;
            }
            JsonNode root = objectMapper.readTree(response.body());
            String aud = root.path("aud").asText(null);
            if (!clientId.equals(aud)) {
                log.warn("Google ID token audience mismatch - possible token-substitution attempt");
                return null;
            }
            if (!root.path("email_verified").asText("false").equals("true")) {
                return null;
            }
            String sub = root.path("sub").asText(null);
            String email = root.path("email").asText(null);
            String name = root.path("name").asText(null);
            if (sub == null || email == null) return null;
            return new Verified(sub, email.toLowerCase(), name != null ? name : email);
        } catch (Exception e) {
            log.error("Google ID token verification failed: {}", e.getMessage());
            return null;
        }
    }

    public record Verified(String googleId, String email, String name) {
    }
}
