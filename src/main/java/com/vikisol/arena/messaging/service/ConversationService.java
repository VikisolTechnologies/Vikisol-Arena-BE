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
import java.util.Collections;
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
    private final com.vikisol.arena.posts.repository.PostRepository postRepository;
    private final com.vikisol.arena.follows.service.BlockService blockService;
    private final com.vikisol.arena.platform.service.ModerationService moderationService;

    static final int MAX_ANONYMOUS_CHATS_PER_DAY = 10;

    @Transactional(readOnly = true)
    public List<ConversationResponse> getMyConversations(UUID userId) {
        return conversationRepository.findAllForUser(userId).stream().map(c -> toResponse(c, userId)).toList();
    }

    @Transactional(readOnly = true)
    public List<ThreadMessageResponse> getMessages(UUID userId, UUID conversationId) {
        Conversation conversation = requireConversation(conversationId);
        assertParticipant(userId, conversation);
        List<ThreadMessage> messages = threadMessageRepository.findTop100ByConversationIdOrderByCreatedAtDesc(conversationId);
        Collections.reverse(messages); // most-recent-first from the query -> ascending for display
        return messages.stream().map(m -> toResponse(m, userId)).toList();
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

    /**
     * POST /messages/conversations. A plain request (a person, not anonymous) is the existing
     * one-DM-per-pair getOrCreate. Otherwise (Phase 2 part C) it's an anonymous chat: to a post's
     * author (hidden too if the post is anonymous) and/or with yourself hidden. Anonymous chats
     * are separate rows from the pair's normal DM and are reused per (people, post, who's hidden).
     */
    @Transactional
    public ConversationResponse start(UUID userId, com.vikisol.arena.messaging.dto.CreateConversationRequest request) {
        boolean hideMe = Boolean.TRUE.equals(request.anonymous());
        com.vikisol.arena.posts.entity.Post post = null;
        UUID recipientId;
        if (request.postId() != null && !request.postId().isBlank()) {
            post = postRepository.findById(parseId(request.postId(), "That post doesn't exist"))
                    .orElseThrow(() -> new ResourceNotFoundException("That post doesn't exist"));
            recipientId = post.getAuthorUser().getId();
        } else if (request.participantUserId() != null && !request.participantUserId().isBlank()) {
            recipientId = parseId(request.participantUserId(), "That person doesn't exist");
        } else {
            throw new BadRequestException("Say who you want to message");
        }
        boolean hideThem = post != null && post.isAnonymous();
        if (!hideMe && !hideThem) {
            return getOrCreate(userId, recipientId, request.context());
        }

        if (userId.equals(recipientId)) {
            // Same answer whether or not the post is anonymous, so it can't be used as a probe.
            throw new BadRequestException("That's your own post.");
        }
        if (blockService.isBlockedEitherDirection(userId, recipientId)) {
            throw new BadRequestException("You can't message this person.");
        }
        List<Conversation> between = conversationRepository.findAllBetween(userId, recipientId);
        // Whoever closed an anonymous chat with you has said no - no new ones from you.
        boolean refused = between.stream().anyMatch(c -> c.isAnonymous() && c.getClosedBy() != null && c.getClosedBy().getId().equals(recipientId));
        if (refused) {
            throw new BadRequestException("This person isn't accepting anonymous messages from you.");
        }
        UUID postId = post == null ? null : post.getId();
        var existing = between.stream()
                .filter(c -> c.getClosedAt() == null)
                .filter(c -> java.util.Objects.equals(c.getPost() == null ? null : c.getPost().getId(), postId))
                .filter(c -> hiddenFlag(c, userId) == hideMe && hiddenFlag(c, recipientId) == hideThem)
                .findFirst();
        if (existing.isPresent()) return toResponse(existing.get(), userId);

        if (conversationRepository.countAnonymousStartedSince(userId, Instant.now().minus(java.time.Duration.ofDays(1))) >= MAX_ANONYMOUS_CHATS_PER_DAY) {
            throw new BadRequestException("You've started the most anonymous chats allowed today - try again tomorrow.");
        }
        String context = request.context();
        if ((context == null || context.isBlank()) && post != null) {
            String about = post.getTitle() != null && !post.getTitle().isBlank() ? post.getTitle() : post.getBody();
            context = "About: " + (about.length() > 80 ? about.substring(0, 80) + "…" : about);
        }
        Conversation conversation = Conversation.builder()
                .userA(requireUser(userId)).userB(requireUser(recipientId))
                .anonymousA(hideMe).anonymousB(hideThem)
                .post(post).context(context).lastMessageAt(Instant.now())
                .build();
        return toResponse(conversationRepository.save(conversation), userId);
    }

    @Transactional
    public ConversationResponse close(UUID userId, UUID conversationId) {
        Conversation conversation = requireConversation(conversationId);
        assertParticipant(userId, conversation);
        if (conversation.getClosedAt() == null) {
            conversation.setClosedAt(Instant.now());
            conversation.setClosedBy(requireUser(userId));
        }
        return toResponse(conversation, userId);
    }

    @Transactional
    public void report(UUID userId, UUID conversationId, String reason) {
        Conversation conversation = requireConversation(conversationId);
        assertParticipant(userId, conversation);
        moderationService.fileConversationReport(requireUser(userId), conversation,
                reason == null || reason.isBlank() ? "Reported from chat" : reason.trim());
    }

    private static boolean hiddenFlag(Conversation c, UUID userId) {
        return c.getUserA().getId().equals(userId) ? c.isAnonymousA() : c.isAnonymousB();
    }

    private static UUID parseId(String raw, String notFound) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(notFound);
        }
    }

    @Transactional
    public ThreadMessageResponse sendMessage(UUID userId, UUID conversationId, String content) {
        Conversation conversation = requireConversation(conversationId);
        assertParticipant(userId, conversation);
        if (conversation.getClosedAt() != null) {
            throw new BadRequestException("This chat was closed.");
        }

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
        // A hidden sender stays hidden in the notification too.
        boolean senderHidden = hiddenFlag(conversation, userId);
        notificationService.notify(recipient, NotificationType.SYSTEM, "New message",
                senderHidden ? "Someone sent you an anonymous message." : requireUser(userId).getName() + " sent you a message.");

        // Same "only audit the enterprise side" scoping as InterviewService.propose() - a
        // conversation can be sent from either participant. findEntityForUser() (not
        // getEntityForUser() - see that method's own comment) so a candidate sender - the
        // common case - never crosses a @Transactional boundary via a thrown exception, which
        // used to silently doom this entire transaction before it ever reached commit.
        enterpriseProfileService.findEntityForUser(userId)
                .ifPresent(tenant -> auditService.record(tenant.getId(), userId, AuditActions.MESSAGE_SENT,
                        // Tenant admins read this log - a hidden recipient's name never goes in it.
                        hiddenFlag(conversation, recipient.getId()) ? "an anonymous chat" : recipient.getName()));

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
            // recruiters happens to be on the other end. findEntityForUser() (see its own
            // comment) - the "hiring_manager not yet linked" case below is real and used to
            // silently doom this read transaction via the same exception-crosses-a-
            // @Transactional-boundary mechanism as sendMessage()'s audit call.
            var tenant = enterpriseProfileService.findEntityForUser(other.getId());
            if (tenant.isPresent()) {
                displayName = tenant.get().getCompanyName();
                displayEmoji = tenant.get().getLogoEmoji();
            }
            // else: no resolvable tenant (e.g. a hiring_manager not yet linked) - falls back to
            // the user's own name, set above.
        }

        boolean otherHidden = hiddenFlag(c, other.getId());
        boolean meHidden = hiddenFlag(c, viewingUserId);
        String participantId = other.getId().toString();
        if (otherHidden) {
            participantId = "";
            displayName = com.vikisol.arena.common.util.AnonymousAlias.of("chat:" + c.getId() + ":" + (viewerIsA ? "B" : "A"));
            displayEmoji = com.vikisol.arena.common.util.AnonymousAlias.EMOJI;
        }
        return new ConversationResponse(c.getId().toString(), participantId, displayName, displayEmoji,
                c.getContext(), c.getLastMessageAt().toString(), unread,
                otherHidden, meHidden, c.getClosedAt() != null, c.getPost() == null ? null : c.getPost().getId().toString());
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
