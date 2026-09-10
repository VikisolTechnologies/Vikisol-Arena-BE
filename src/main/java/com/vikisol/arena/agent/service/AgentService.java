package com.vikisol.arena.agent.service;

import com.vikisol.arena.agent.client.AgentContext;
import com.vikisol.arena.agent.client.AgentHistoryEntry;
import com.vikisol.arena.agent.client.AgentReply;
import com.vikisol.arena.agent.client.AgentServiceClient;
import com.vikisol.arena.agent.dto.AgentConversationResponse;
import com.vikisol.arena.agent.dto.AgentMessageResponse;
import com.vikisol.arena.agent.entity.AgentConversation;
import com.vikisol.arena.agent.entity.AgentMessage;
import com.vikisol.arena.agent.entity.AgentMessageRole;
import com.vikisol.arena.agent.repository.AgentConversationRepository;
import com.vikisol.arena.agent.repository.AgentMessageRepository;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentService {

    // Exact copy mandated by ARENA-DOCUMENT-3 §3/§14 for when the real agent backend can't be
    // reached - never a fabricated response, never an indefinite "Thinking...".
    private static final String UNAVAILABLE_MESSAGE =
            "The agent is temporarily unavailable. Your Arena account is still working normally.";

    private final AgentConversationRepository conversationRepository;
    private final AgentMessageRepository messageRepository;
    private final AgentServiceClient agentServiceClient;
    private final UserRepository userRepository;

    // One running conversation per user for v1 - matches what the old /agent page actually gave
    // users (a single chat thread), just persisted server-side now instead of React state. Real
    // multi-conversation history (a thread list) is a frontend feature to layer on later, not a
    // backend gap - the schema (conversation_id FK) already supports it.
    @Transactional
    public AgentConversationResponse getOrCreateActiveConversation(UUID userId) {
        AgentConversation conversation = conversationRepository.findFirstByUserIdOrderByUpdatedAtDesc(userId)
                .orElseGet(() -> {
                    User user = userRepository.getReferenceById(userId);
                    return conversationRepository.save(AgentConversation.builder().user(user).build());
                });
        return toConversationResponse(conversation);
    }

    @Transactional(readOnly = true)
    public List<AgentMessageResponse> getMessages(UUID userId, UUID conversationId) {
        AgentConversation conversation = requireOwnedConversation(userId, conversationId);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId()).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @Transactional
    public AgentMessageResponse sendMessage(UUID userId, UUID conversationId, String content) {
        AgentConversation conversation = requireOwnedConversation(userId, conversationId);

        messageRepository.save(AgentMessage.builder()
                .conversation(conversation).role(AgentMessageRole.USER).content(content).build());

        AgentMessage reply;
        if (agentServiceClient.isAvailable()) {
            List<AgentHistoryEntry> history = messageRepository
                    .findTop20ByConversationIdOrderByCreatedAtDesc(conversation.getId()).stream()
                    .sorted(Comparator.comparing(AgentMessage::getCreatedAt))
                    .map(m -> new AgentHistoryEntry(m.getRole().name().toLowerCase(), m.getContent()))
                    .toList();
            User user = userRepository.getReferenceById(userId);
            AgentReply agentReply = agentServiceClient.sendMessage(
                    new AgentContext(userId, user.getRole().name()), history, content);
            reply = messageRepository.save(AgentMessage.builder()
                    .conversation(conversation).role(AgentMessageRole.AGENT).content(agentReply.content()).build());
        } else {
            reply = messageRepository.save(AgentMessage.builder()
                    .conversation(conversation).role(AgentMessageRole.AGENT)
                    .content(UNAVAILABLE_MESSAGE).serviceUnavailable(true).build());
        }

        conversation.setUpdatedAt(java.time.Instant.now());
        conversationRepository.save(conversation);
        return toMessageResponse(reply);
    }

    private AgentConversation requireOwnedConversation(UUID userId, UUID conversationId) {
        AgentConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + conversationId));
        if (!conversation.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Not your conversation");
        }
        return conversation;
    }

    private AgentConversationResponse toConversationResponse(AgentConversation c) {
        return new AgentConversationResponse(c.getId(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt());
    }

    private AgentMessageResponse toMessageResponse(AgentMessage m) {
        return new AgentMessageResponse(
                m.getId(), m.getRole().name().toLowerCase(), m.getContent(), m.isServiceUnavailable(), m.getCreatedAt());
    }
}
