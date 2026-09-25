package com.vikisol.arena.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikisol.arena.agent.client.*;
import com.vikisol.arena.agent.service.AgentService;
import com.vikisol.arena.auth.entity.*;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.schema.EmbeddedPostgresAppTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentApprovalFlowTest extends EmbeddedPostgresAppTest {
    @Autowired AgentService service;
    @Autowired UserRepository users;
    @Autowired ObjectMapper json;
    @MockBean(name = "agentServiceClient") AgentServiceClient gateway;

    User user() { return users.save(User.builder().name("Test").email(UUID.randomUUID() + "@test.local")
            .passwordHash("x").role(Role.TALENT).build()); }

    @Test void proposalsPersistAndOnlyTheOwnerCanExplicitlyExecuteOnce() throws Exception {
        var user = user(); var other = user();
        var conversation = service.getOrCreateActiveConversation(user.getId());
        when(gateway.isAvailable()).thenReturn(true);
        when(gateway.sendMessage(any(), anyList(), anyString())).thenReturn(new AgentReply("Review before joining", List.of(
                new AgentReply.ProposedAction(UUID.randomUUID().toString(), "arena.joinActivity",
                        json.readTree("{\"postId\":\"p1\"}"), Instant.now().plusSeconds(300)))));
        var reply = service.sendMessage(user.getId(), conversation.id(), "Join the game");
        verify(gateway).sendMessage(any(), eq(List.of()), eq("Join the game"));
        verify(gateway, never()).decideAction(any(), anyString(), anyBoolean());
        assertThat(service.getMessages(user.getId(), conversation.id()).getLast().actions()).hasSize(1);
        var actionId = reply.actions().getFirst().id();
        assertThatThrownBy(() -> service.decideAction(other.getId(), actionId, true)).hasMessageContaining("Action not found");
        when(gateway.decideAction(any(), anyString(), eq(true))).thenReturn(new AgentDecision("done", json.readTree("{\"status\":\"approved\"}"), null));
        assertThat(service.decideAction(user.getId(), actionId, true).status()).isEqualTo("done");
        assertThat(service.decideAction(user.getId(), actionId, true).status()).isEqualTo("done");
        verify(gateway, times(1)).decideAction(any(), anyString(), eq(true));
    }

    @Test void unavailableProviderPreservesTheMessageAndReportsUnavailable() {
        var user = user(); var conversation = service.getOrCreateActiveConversation(user.getId());
        when(gateway.isAvailable()).thenReturn(true);
        when(gateway.sendMessage(any(), anyList(), anyString())).thenThrow(new RealAgentServiceClient.AgentServiceException("offline"));
        assertThat(service.sendMessage(user.getId(), conversation.id(), "Hello").serviceUnavailable()).isTrue();
        assertThat(service.getMessages(user.getId(), conversation.id())).hasSize(2);
    }
}
