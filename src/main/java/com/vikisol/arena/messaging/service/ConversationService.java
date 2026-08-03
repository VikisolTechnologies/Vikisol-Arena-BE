package com.vikisol.arena.messaging.service;

import com.vikisol.arena.audit.AuditActions;
import com.vikisol.arena.audit.AuditService;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.enterprise.service.EnterpriseProfileService;
import com.vikisol.arena.messaging.dto.ConversationResponse;
import com.vikisol.arena.messaging.dto.ThreadMessageResponse;
import com.vikisol.arena.messaging.entity.Conversation;
import com.vikisol.arena.messaging.entity.ThreadMessage;
import com.vikisol.arena.messaging.repository.ConversationRepository;
import com.vikisol.arena.messaging.repository.ThreadMessageRepository;
import com.vikisol.arena.notifications.entity.NotificationType;
import com.vikisol.arena.notifications.service.NotificationService;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ThreadMessageRepository threadMessageRepository;
    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final EnterpriseProfileService enterpriseProfileService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<ConversationResponse> getMyConversations(UUID userId) {
        return conversationRepository.findAllForUser(userId).stream().map(c -> toResponse(c, userId)).toList();
    }

    @Transactional(readOnly = true)
    public List<ThreadMessageResponse> getMessages(UUID userId, UUID conversationId) {
        Conversation conversation = requireConversation(conversationId);
        assertParticipant(userId, conversation);
        return threadMessageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(m -> toResponse(m, userId)).toList();
    }

    @Transactional
    public ConversationResponse getOrCreate(UUID userId, UUID participantUserId, String context) {
        if (userId.equals(participantUserId)) {
            throw new BadRequestException("Cannot start a conversation with yourself");
        }
        var existing = conversationRepository.findBetween(userId, participantUserId);
        if (existing.isPresent()) {
            return toResponse(existing.get(), userId);
        }
        User a = requireUser(userId);
        User b = requireUser(participantUserId);
        Conversation conversation = Conversation.builder()
                .userA(a).userB(b).context(context).lastMessageAt(Instant.now())
                .build();
        return toResponse(conversationRepository.save(conversation), userId);
    }

    @Transactional
    public ThreadMessageResponse sendMessage(UUID userId, UUID conversationId, String content) {
        Conversation conversation = requireConversation(conversationId);
        assertParticipant(userId, conversation);

        ThreadMessage message = threadMessageRepository.save(ThreadMessage.builder()
                .conversation(conversation).sender(requireUser(userId)).content(content).build());

        conversation.setLastMessageAt(message.getCreatedAt());
        if (conversation.getUserA().getId().equals(userId)) {
            conversation.setLastReadAtA(message.getCreatedAt());
        } else {
            conversation.setLastReadAtB(message.getCreatedAt());
        }
        conversationRepository.save(conversation);

        User recipient = conversation.getUserA().getId().equals(userId) ? conversation.getUserB() : conversation.getUserA();
        notificationService.notify(recipient, NotificationType.SYSTEM, "New message", requireUser(userId).getName() + " sent you a message.");

        // Same "only audit the enterprise side" scoping as InterviewService.propose() - a
        // conversation can be sent from either participant.
        try {
            var tenant = enterpriseProfileService.getEntityForUser(userId);
            auditService.record(tenant.getId(), userId, AuditActions.MESSAGE_SENT, recipient.getName());
        } catch (ResourceNotFoundException ignored) {
            // Candidate sent it - nothing to audit.
        }

        return toResponse(message, userId);
    }

    private void assertParticipant(UUID userId, Conversation conversation) {
        boolean participant = conversation.getUserA().getId().equals(userId) || conversation.getUserB().getId().equals(userId);
        if (!participant) {
            throw new AccessDeniedException("Not part of this conversation");
        }
    }

    private ConversationResponse toResponse(Conversation c, UUID viewingUserId) {
        boolean viewerIsA = c.getUserA().getId().equals(viewingUserId);
        User other = viewerIsA ? c.getUserB() : c.getUserA();
        Instant lastReadAt = viewerIsA ? c.getLastReadAtA() : c.getLastReadAtB();
        boolean unread = lastReadAt == null || lastReadAt.isBefore(c.getLastMessageAt());

        String displayName = other.getName();
        String displayEmoji = "🧑🏽";
        if (other.getRole() == Role.TALENT) {
            var profile = candidateProfileRepository.findByUserId(other.getId());
            if (profile.isPresent()) {
                displayName = profile.get().getName();
                displayEmoji = profile.get().getAvatarEmoji();
            }
        } else {
            // Always the tenant's identity (company name/logo), not the individual recruiter's -
            // a candidate messaging "Swiggy" should see Swiggy regardless of which of Swiggy's
            // recruiters happens to be on the other end.
            try {
                var tenant = enterpriseProfileService.getEntityForUser(other.getId());
                displayName = tenant.getCompanyName();
                displayEmoji = tenant.getLogoEmoji();
            } catch (ResourceNotFoundException ignored) {
                // No resolvable tenant (e.g. a hiring_manager not yet linked) - falls back to
                // the user's own name, set above.
            }
        }

        return new ConversationResponse(c.getId().toString(), other.getId().toString(), displayName, displayEmoji,
                c.getContext(), c.getLastMessageAt().toString(), unread);
    }

    private ThreadMessageResponse toResponse(ThreadMessage m, UUID viewingUserId) {
        return new ThreadMessageResponse(m.getId().toString(), m.getConversation().getId().toString(),
                m.getSender().getId().equals(viewingUserId), m.getContent(), m.getCreatedAt().toString());
    }

    private Conversation requireConversation(UUID id) {
        return conversationRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + id));
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }
}
