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

/**
 * Calls JennySol's real agent gateway ({@code POST /api/agent/gateway/chat}) - see the JennySol
 * repository's {@code routes/agentGateway.ts} (M6, PROJECT-PROGRESS.md milestone model). Same
 * plain {@code java.net.http.HttpClient} style as {@link com.vikisol.arena.integration.provider.Msg91PhoneOtpProvider}/
 * {@code ResendEmailProvider} - deliberately not a {@code @Component}; constructed by
 * {@link AgentProviderConfig} from env-var-backed configuration instead, same reason those
 * classes give for the same choice.
 * <p>
 * Mints a fresh, short-lived service token via {@link AgentServiceTokenIssuer} on every call -
 * per ADR-003 in the JennySol repository, a token is never cached or reused across requests.
 * <p>
 * <b>History is not sent to the gateway yet.</b> JennySol's M6 gateway is deliberately single-turn
 * (stateless, no conversation persistence at that boundary - see its own class-doc for why); this
 * client's {@code history} parameter is therefore currently unused. Multi-turn context is real,
 * scoped future work once the gateway itself grows a history-aware contract - not silently
 * dropped without comment, flagged here and in PROJECT-PROGRESS.md's M6 entry.
 * <p>
 * <b>NOT independently verified against a live JennySol deployment</b> - same "isAvailable()
 * correctly stays false, dormant" status Msg91PhoneOtpProvider had before a real account existed.
 * {@code isAvailable()} requires both a configured gateway URL and a configured token issuer, so
 * this stays inert (never becomes the {@code @Primary} bean over {@link NoopAgentServiceClient})
 * until an operator deliberately sets both {@code JENNYSOL_GATEWAY_URL} and
 * {@code SERVICE_TOKEN_SECRET_ARENA} to a value JennySol's own deployment is also configured
 * with - a real production-rollout decision, not something this code activates on its own.
 */
@Slf4j
public class RealAgentServiceClient implements AgentServiceClient {

    // Every authenticated user gets the read tool. arena.applyToJob (M7) is additionally granted
    // only to TALENT accounts - matching ApplicationController's own
    // @PreAuthorize("hasRole('TALENT')") on POST /applications, so a recruiter/company_admin
    // token is never even offered a tool their real Arena role could never use, on top of (not
    // instead of) the independent server-side scope re-check ToolRegistry/the round-trip
    // AgentServiceTokenAuthenticationFilter both still perform.
    private static final List<String> READ_ONLY_SCOPE = List.of("arena.searchJobs");
    private static final List<String> TALENT_SCOPE = List.of("arena.searchJobs", "arena.applyToJob");

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
            String requestBody = OBJECT_MAPPER.writeValueAsString(new ChatRequestBody(message));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(gatewayBaseUrl + "/api/agent/gateway/chat"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = OBJECT_MAPPER.readTree(response.body());

            if (response.statusCode() != 200) {
                String error = body.has("error") ? body.get("error").asText() : "Unknown error";
                log.warn("JennySol agent gateway returned {}: {}", response.statusCode(), error);
                throw new AgentServiceException("Agent request failed: " + error);
            }

            return new AgentReply(body.path("content").asText(""));
        } catch (AgentServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to reach JennySol agent gateway", e);
            throw new AgentServiceException("Could not reach the agent service", e);
        }
    }

    private record ChatRequestBody(String message) {}

    public static class AgentServiceException extends RuntimeException {
        public AgentServiceException(String message) {
            super(message);
        }

        public AgentServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
