package com.vikisol.arena.agent.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class AgentGatewayContractTest {
    private static final String SECRET = "contract-test-service-secret-not-for-production-123";

    @Test void forwardsHistoryAndParsesProposalsWithoutExecutingThem() throws Exception {
        var json = new ObjectMapper();
        var received = new AtomicReference<JsonNode>();
        var auth = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String actionId = UUID.randomUUID().toString();
        server.createContext("/api/agent/gateway/chat", exchange -> {
            received.set(json.readTree(exchange.getRequestBody()));
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = ("{\"content\":\"Review this request\",\"pendingActions\":[{\"actionId\":\"" + actionId +
                    "\",\"toolName\":\"arena.joinActivity\",\"args\":{\"postId\":\"p1\"},\"expiresAt\":\"2030-01-01T00:00:00Z\"}]}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var client = new RealAgentServiceClient("http://127.0.0.1:" + server.getAddress().getPort(), new AgentServiceTokenIssuer(SECRET));
            UUID userId = UUID.randomUUID();
            var reply = client.sendMessage(new AgentContext(userId, "TALENT", "Name: Test"),
                    List.of(new AgentHistoryEntry("user", "Find a game"), new AgentHistoryEntry("agent", "Here is one")), "Join it");
            assertThat(received.get().path("message").asText()).isEqualTo("Join it");
            assertThat(received.get().path("history").size()).isEqualTo(2);
            assertThat(received.get().path("history").get(1).path("role").asText()).isEqualTo("assistant");
            assertThat(received.get().path("context").asText()).isEqualTo("Name: Test");
            var claims = new AgentServiceTokenVerifier(SECRET).verify(auth.get().substring(7));
            assertThat(claims.userId()).isEqualTo(userId);
            assertThat(claims.scope()).contains("arena.joinActivity");
            assertThat(reply.pendingActions()).hasSize(1);
            assertThat(reply.pendingActions().getFirst().actionId()).isEqualTo(actionId);
        } finally { server.stop(0); }
    }

    @Test void approvalUsesOpaqueIdAndAnExplicitDecision() throws Exception {
        var json = new ObjectMapper();
        var received = new AtomicReference<JsonNode>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String actionId = UUID.randomUUID().toString();
        server.createContext("/api/agent/gateway/actions/" + actionId, exchange -> {
            received.set(json.readTree(exchange.getRequestBody()));
            byte[] body = "{\"status\":\"executed\",\"result\":{\"status\":\"pending\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var client = new RealAgentServiceClient("http://127.0.0.1:" + server.getAddress().getPort(), new AgentServiceTokenIssuer(SECRET));
            var result = client.decideAction(new AgentContext(UUID.randomUUID(), "TALENT"), actionId, true);
            assertThat(received.get().size()).isEqualTo(1);
            assertThat(received.get().path("approve").asBoolean()).isTrue();
            assertThat(result.status()).isEqualTo("done");
            assertThat(result.result().path("status").asText()).isEqualTo("pending");
        } finally { server.stop(0); }
    }

    @Test void aDefiniteRefusalIsFailedButAServerErrorStaysUnknown() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String refused = UUID.randomUUID().toString();
        String broken = UUID.randomUUID().toString();
        server.createContext("/api/agent/gateway/actions/" + refused, exchange -> {
            byte[] body = "{\"status\":\"failed\",\"error\":\"This activity is full\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(422, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/api/agent/gateway/actions/" + broken, exchange -> {
            byte[] body = "{\"error\":\"Arena timed out\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(502, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var client = new RealAgentServiceClient("http://127.0.0.1:" + server.getAddress().getPort(), new AgentServiceTokenIssuer(SECRET));
            var failed = client.decideAction(new AgentContext(UUID.randomUUID(), "TALENT"), refused, true);
            assertThat(failed.status()).isEqualTo("failed");
            assertThat(failed.error()).isEqualTo("This activity is full");
            assertThat(client.decideAction(new AgentContext(UUID.randomUUID(), "TALENT"), broken, true).status()).isEqualTo("unknown");
        } finally { server.stop(0); }
    }
}
