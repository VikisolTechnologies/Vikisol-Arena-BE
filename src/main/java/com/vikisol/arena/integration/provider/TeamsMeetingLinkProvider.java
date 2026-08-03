package com.vikisol.arena.integration.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Microsoft Graph (Teams) implementation of {@link MeetingLinkProvider} - OAuth2
 * client-credentials (app-only) flow, structurally the same as HRLMS-BE's
 * {@code Microsoft365Provider}: once an Azure AD app is registered for Arena (a separate app
 * registration from HRLMS's own - this is an unconfigured, independent integration), every call
 * creates a calendar event with {@code isOnlineMeeting: true} under a fixed organizer mailbox
 * ({@code TEAMS_ORGANIZER_EMAIL}) and Graph returns the Teams join URL on the response.
 * <p>
 * NOT independently verified against a live Azure tenant in this session - no tenant/app
 * registration exists for Arena yet. Request/response shapes follow Microsoft's published Graph
 * v1.0 API contract; should be smoke-tested against a real tenant before being relied on.
 * <p>
 * Deliberately NOT a {@code @Component} - see {@link ResendEmailProvider}'s javadoc for why.
 */
@Slf4j
public class TeamsMeetingLinkProvider implements MeetingLinkProvider {

    private final String tenantId;
    private final String clientId;
    private final String clientSecret;
    private final String organizerEmail;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private static final String GRAPH_BASE = "https://graph.microsoft.com/v1.0";

    // Cached app-only token - avoids a fresh token request on every meeting creation (tokens are
    // valid ~60-90 minutes); refreshed a minute early to avoid edge-of-expiry failures. Same
    // approach as Microsoft365Provider.
    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;
    private final Object tokenLock = new Object();

    public TeamsMeetingLinkProvider(String tenantId, String clientId, String clientSecret, String organizerEmail) {
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.organizerEmail = organizerEmail;
    }

    @Override
    public String getProviderName() {
        return "Microsoft Teams (Graph)";
    }

    @Override
    public boolean isConfigured() {
        return notBlank(tenantId) && notBlank(clientId) && notBlank(clientSecret) && notBlank(organizerEmail);
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) return cachedToken;
        synchronized (tokenLock) {
            if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) return cachedToken;
            try {
                String form = "client_id=" + java.net.URLEncoder.encode(clientId, "UTF-8")
                        + "&client_secret=" + java.net.URLEncoder.encode(clientSecret, "UTF-8")
                        + "&scope=" + java.net.URLEncoder.encode("https://graph.microsoft.com/.default", "UTF-8")
                        + "&grant_type=client_credentials";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token"))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build();
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 300) {
                    throw new RuntimeException("Azure AD token request failed (" + response.statusCode() + "): " + response.body());
                }
                JsonNode json = objectMapper.readTree(response.body());
                cachedToken = json.get("access_token").asText();
                int expiresInSeconds = json.has("expires_in") ? json.get("expires_in").asInt() : 3600;
                tokenExpiresAt = Instant.now().plusSeconds(Math.max(60, expiresInSeconds - 60));
                return cachedToken;
            } catch (Exception e) {
                throw new RuntimeException("Could not acquire Microsoft Graph access token: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public String createMeetingLink(UUID interviewId, String subject, Instant startTime, int durationMinutes, List<String> attendeeEmails) {
        try {
            Instant end = startTime.plusSeconds(durationMinutes * 60L);
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("subject", subject);
            body.put("start", Map.of("dateTime", startTime.atOffset(ZoneOffset.UTC).toLocalDateTime().toString(), "timeZone", "UTC"));
            body.put("end", Map.of("dateTime", end.atOffset(ZoneOffset.UTC).toLocalDateTime().toString(), "timeZone", "UTC"));
            body.put("attendees", (attendeeEmails == null ? List.<String>of() : attendeeEmails).stream()
                    .map(email -> Map.of("emailAddress", Map.of("address", email), "type", "required"))
                    .toList());
            body.put("isOnlineMeeting", true);
            body.put("onlineMeetingProvider", "teamsForBusiness");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GRAPH_BASE + "/users/" + organizerEmail + "/events"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + getAccessToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new RuntimeException("Graph event creation failed (" + response.statusCode() + "): " + response.body());
            }
            JsonNode json = objectMapper.readTree(response.body());
            JsonNode onlineMeeting = json.get("onlineMeeting");
            String joinUrl = onlineMeeting != null && onlineMeeting.has("joinUrl") ? onlineMeeting.get("joinUrl").asText() : null;
            if (joinUrl == null || joinUrl.isBlank()) {
                throw new RuntimeException("Graph event created but no Teams joinUrl was returned");
            }
            log.info("Teams meeting created for interview {}: {}", interviewId, joinUrl);
            return joinUrl;
        } catch (Exception e) {
            log.error("Teams meeting creation failed for interview {}: {}", interviewId, e.getMessage());
            throw new RuntimeException("Could not create Teams meeting: " + e.getMessage(), e);
        }
    }
}
