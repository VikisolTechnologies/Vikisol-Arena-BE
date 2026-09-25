package com.vikisol.arena.agent.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/** Sends bounded history and explicit approvals to JennySol with fresh scoped service tokens. */
@Slf4j
public class RealAgentServiceClient implements AgentServiceClient {

    // Every authenticated user gets the read tool. arena.applyToJob (M7) is additionally granted
    // only to TALENT accounts - matching ApplicationController's own
    // @PreAuthorize("hasRole('TALENT')") on POST /applications, so a recruiter/company_admin
    // token is never even offered a tool their real Arena role could never use, on top of (not
    // instead of) the independent server-side scope re-check ToolRegistry/the round-trip
    // AgentServiceTokenAuthenticationFilter both still perform.
    private static final List<String> READ_ONLY_SCOPE = List.of("arena.searchJobs", "arena.search", "arena.nearbyActivities", "arena.listCommunities");
    private static final List<String> TALENT_SCOPE = List.of("arena.searchJobs", "arena.search", "arena.nearbyActivities",
            "arena.listCommunities", "arena.applyToJob", "arena.createPost", "arena.joinActivity", "arena.createProject", "arena.placeBid");

    // Package-private (not private) so RealAgentServiceClientTest can assert on it directly -
    // this class makes no live HTTP calls in its own test suite (see that file's own class doc),
    // so scope selection needs a seam that doesn't require standing up a fake gateway.
    static List<String> scopeFor(String role) {
        return "TALENT".equals(role) ? TALENT_SCOPE : READ_ONLY_SCOPE;
    }

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String gatewayBaseUrl;
    private final AgentServiceTokenIssuer tokenIssuer;

    public RealAgentServiceClient(String gatewayBaseUrl, AgentServiceTokenIssuer tokenIssuer) {
        this.gatewayBaseUrl = gatewayBaseUrl;
        this.tokenIssuer = tokenIssuer;
    }

    @Override
    public boolean isAvailable() {
        return gatewayBaseUrl != null && !gatewayBaseUrl.isBlank() && tokenIssuer.isConfigured();
    }

    @Override
    public AgentReply sendMessage(AgentContext context, List<AgentHistoryEntry> history, String message) {
        if (!isAvailable()) {
            throw new IllegalStateException("RealAgentServiceClient is not configured - isAvailable() must be checked first");
        }

        String token = tokenIssuer.issue(context.userId().toString(), context.role(), null, scopeFor(context.role()));

        try {
            String requestBody = OBJECT_MAPPER.writeValueAsString(new ChatRequestBody(message,
                    history.stream().skip(Math.max(0, history.size() - 20))
                            .map(turn -> new AgentHistoryEntry("user".equals(turn.role()) ? "user" : "assistant",
                                    turn.content().substring(0, Math.min(4000, turn.content().length())))).toList(),
                    context.context()));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(gatewayBaseUrl + "/api/agent/gateway/chat"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = OBJECT_MAPPER.readTree(response.body());

            if (response.statusCode() != 200) {
                String error = body.has("error") ? body.get("error").asText() : "Unknown error";
                log.warn("JennySol agent gateway returned {}: {}", response.statusCode(), error);
                throw new AgentServiceException("Agent request failed: " + error);
            }

            var actions = new java.util.ArrayList<AgentReply.ProposedAction>();
            for (JsonNode action : body.path("pendingActions")) {
                String name = action.path("toolName").asText();
                String id = action.path("actionId").asText();
                if (id.isBlank() || id.length() > 100 || !scopeFor(context.role()).contains(name) || !action.path("args").isObject()) {
                    throw new AgentServiceException("Invalid action returned by agent service");
                }
                java.time.Instant expiresAt = action.hasNonNull("expiresAt")
                        ? java.time.Instant.parse(action.get("expiresAt").asText()) : java.time.Instant.now().plusSeconds(300);
                actions.add(new AgentReply.ProposedAction(id, name, action.get("args"), expiresAt));
            }
            return new AgentReply(body.path("content").asText(""), actions);
        } catch (AgentServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to reach JennySol agent gateway", e);
            throw new AgentServiceException("Could not reach the agent service", e);
        }
    }

    @Override
    public AgentDecision decideAction(AgentContext context, String actionId, boolean approve) {
        if (!isAvailable()) throw new IllegalStateException("Agent service is unavailable");
        // Only opaque UUIDs supplied by the gateway may form a path segment.
        java.util.UUID.fromString(actionId);
        String token = tokenIssuer.issue(context.userId().toString(), context.role(), null, scopeFor(context.role()));
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(gatewayBaseUrl + "/api/agent/gateway/actions/" + actionId))
                    .header("Content-Type", "application/json").header("Authorization", "Bearer " + token)
                    .timeout(Duration.ofSeconds(25))
                    .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(java.util.Map.of("approve", approve))))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = OBJECT_MAPPER.readTree(response.body());
            if (response.statusCode() == 200 && "executed".equals(body.path("status").asText()))
                return new AgentDecision("done", body.get("result"), null);
            if (response.statusCode() == 200 && "rejected".equals(body.path("status").asText()))
                return new AgentDecision("declined", null, null);
            // 422 = Arena itself answered and refused (full, closed, not allowed): nothing ran.
            if (response.statusCode() == 422 && "failed".equals(body.path("status").asText())) {
                String reason = body.path("error").asText("The action could not be completed.");
                return new AgentDecision("failed", null, reason.substring(0, Math.min(500, reason.length())));
            }
            if ("expired".equals(body.path("code").asText()))
                return new AgentDecision("expired", null, "This proposal has expired. Ask Jenny again.");
            return new AgentDecision("unknown", null, "The action result could not be confirmed. Check Arena before trying again.");
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("Agent action result unavailable: {}", e.getClass().getSimpleName());
            return new AgentDecision("unknown", null, "The action result could not be confirmed. Check Arena before trying again.");
        }
    }

    private record ChatRequestBody(String message, List<AgentHistoryEntry> history, String context) {}

    public static class AgentServiceException extends RuntimeException {
        public AgentServiceException(String message) {
            super(message);
        }

        public AgentServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
