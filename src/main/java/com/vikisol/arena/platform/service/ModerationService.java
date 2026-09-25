package com.vikisol.arena.platform.service;

import com.vikisol.arena.audit.AuditActions;
import com.vikisol.arena.audit.AuditService;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.jobs.entity.JobPosting;
import com.vikisol.arena.jobs.entity.PostingStatus;
import com.vikisol.arena.jobs.repository.JobPostingRepository;
import com.vikisol.arena.platform.dto.ModerationItemResponse;
import com.vikisol.arena.platform.entity.ModerationContentType;
import com.vikisol.arena.platform.entity.ModerationItem;
import com.vikisol.arena.platform.entity.ModerationStatus;
import com.vikisol.arena.platform.repository.ModerationItemRepository;
import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.entity.PostStatus;
import com.vikisol.arena.posts.repository.PostRepository;
import com.vikisol.arena.rooms.entity.Room;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * PA4 (moderation queue). Four sources: autoFlag(JobPosting)/autoFlag(Post) (banned-phrase scan
 * at creation time), fileRoomReport()/filePostReport() (real user reports,
 * ARENA-V2-PRODUCT-ARCHITECTURE.md §4 - called from RoomService.report() and
 * PostController.report() respectively). Depends on PostRepository directly (not PostService)
 * deliberately - PostService already depends on RoomService for room creation, so going through
 * PostService here (RoomService -> ModerationService -> PostService -> RoomService) would be a
 * circular bean dependency. Flipping a Post's status directly for the takedown cases that need
 * it (ROOM/POST-type) is a small enough duplication to avoid that entirely. PostService itself
 * depends the other way (PostService -> ModerationService, to call autoFlag(Post) on create) -
 * that direction is safe since ModerationService never depends back on PostService.
 */
@Service
@RequiredArgsConstructor
public class ModerationService {

    private static final List<String> FLAGGED_PHRASES = List.of(
            "wire transfer fee", "pay to apply", "guaranteed income", "pyramid scheme",
            "unlimited earning potential", "processing fee required"
    );

    private final ModerationItemRepository moderationItemRepository;
    private final JobPostingRepository jobPostingRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final com.vikisol.arena.messaging.repository.ThreadMessageRepository threadMessageRepository;

    @Transactional
    public void autoFlag(JobPosting posting) {
        String haystack = (posting.getTitle() + " " + posting.getDescription()).toLowerCase();
        List<String> matched = FLAGGED_PHRASES.stream().filter(haystack::contains).toList();
        if (matched.isEmpty()) return;
        moderationItemRepository.save(ModerationItem.builder()
                .contentType(ModerationContentType.JOB_POSTING)
                .jobPosting(posting)
                .reason("Flagged terms: " + String.join(", ", matched))
                .status(ModerationStatus.PENDING)
                .build());
    }

    // §4 safety-audit fix: "Rate limits + spam/abuse detection on posting and joining; auto-flag
    // phrases" was built for JobPosting only - the activity layer's own posts (where a scam/
    // abuse phrase is arguably higher-stakes, since it can lead to an in-person meetup) had no
    // auto-flag at all. Same phrase list, same PENDING-queue behavior, called from
    // PostService.create() for every intent type (not just ACTIVITY - an ASK or UPDATE can
    // carry the exact same scam phrases).
    @Transactional
    public void autoFlag(Post post) {
        String haystack = post.getBody().toLowerCase();
        List<String> matched = FLAGGED_PHRASES.stream().filter(haystack::contains).toList();
        if (matched.isEmpty()) return;
        moderationItemRepository.save(ModerationItem.builder()
                .contentType(ModerationContentType.POST)
                .post(post)
                .reason("Auto-flagged terms: " + String.join(", ", matched))
                .status(ModerationStatus.PENDING)
                .build());
    }

    // ARENA-V2-PRODUCT-ARCHITECTURE.md §4 "wired into the platform-admin moderation queue" -
    // called from RoomService.report(). Takes the Room/reporter as already-resolved objects
    // (RoomService already has them in hand) rather than this service re-querying by id.
    @Transactional
    public void fileRoomReport(Room room, User reporter, String reason) {
        moderationItemRepository.save(ModerationItem.builder()
                .contentType(ModerationContentType.ROOM)
                .room(room)
                .reporter(reporter)
                .reason(reason)
                .status(ModerationStatus.PENDING)
                .build());
    }

    // §4's "report ... everywhere (post, room, ...)" - the direct post-level counterpart to
    // fileRoomReport, covering every post regardless of whether it ever grew a Room (UPDATE
    // posts never do; ACTIVITY/ASK posts don't until someone's approved to join).
    @Transactional
    public void filePostReport(UUID reporterUserId, UUID postId, String reason) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        User reporter = userRepository.getReferenceById(reporterUserId);
        moderationItemRepository.save(ModerationItem.builder()
                .contentType(ModerationContentType.POST)
                .post(post)
                .reporter(reporter)
                .reason(reason)
                .status(ModerationStatus.PENDING)
                .build());
    }

    // Phase 2 part C - the abuse path for (usually anonymous) chats. The conversation is passed
    // in by ConversationService, which has already checked the reporter is part of it.
    @Transactional
    public void fileConversationReport(User reporter, com.vikisol.arena.messaging.entity.Conversation conversation, String reason) {
        moderationItemRepository.save(ModerationItem.builder()
                .contentType(ModerationContentType.CONVERSATION)
                .conversation(conversation)
                .reporter(reporter)
                .reason(reason)
                .status(ModerationStatus.PENDING)
                .build());
    }

    @Transactional(readOnly = true)
    public PagedResponse<ModerationItemResponse> listQueue(String statusWire, Pageable pageable) {
        ModerationStatus status = (statusWire == null || statusWire.isBlank())
                ? ModerationStatus.PENDING : ModerationStatus.valueOf(statusWire.trim().toUpperCase());
        return PagedResponse.of(moderationItemRepository.findByStatusOrderByCreatedAtDesc(status, pageable), this::toResponse);
    }

    @Transactional
    public void dismiss(UUID actorUserId, UUID itemId) {
        ModerationItem item = requireItem(itemId);
        item.setStatus(ModerationStatus.DISMISSED);
        item.setResolvedBy(userRepository.getReferenceById(actorUserId));
        item.setResolvedAt(Instant.now());
        moderationItemRepository.save(item);

        if (item.getContentType() == ModerationContentType.JOB_POSTING) {
            auditService.record(item.getJobPosting().getEnterprise().getId(), actorUserId,
                    AuditActions.MODERATION_DISMISSED, item.getJobPosting().getTitle());
        }
        // ROOM/POST-type items have no tenant (candidate-to-candidate, not enterprise-scoped) -
        // no audit call, same "nothing to audit" treatment ConversationService.sendMessage
        // already gives a candidate-sent message with no resolvable tenant.
    }

    @Transactional
    public void takedown(UUID actorUserId, UUID itemId) {
        ModerationItem item = requireItem(itemId);
        item.setStatus(ModerationStatus.TAKEN_DOWN);
        item.setResolvedBy(userRepository.getReferenceById(actorUserId));
        item.setResolvedAt(Instant.now());
        moderationItemRepository.save(item);

        switch (item.getContentType()) {
            case JOB_POSTING -> {
                JobPosting posting = item.getJobPosting();
                posting.setStatus(PostingStatus.CLOSED);
                jobPostingRepository.save(posting);
                auditService.record(posting.getEnterprise().getId(), actorUserId, AuditActions.MODERATION_TAKEDOWN, posting.getTitle());
            }
            case ROOM -> {
                // Cancels the underlying Post directly (not via PostService - see this class's
                // own header comment on why, to avoid a circular bean dependency). Doesn't notify
                // room members the way an author-initiated cancel does (RoomService.
                // notifyRoomOfCancellation) - a deliberate, smaller scope for this rarer admin
                // path; the room's history and membership stay intact, it just stops accepting
                // new joins.
                Post post = item.getRoom().getPost();
                post.setStatus(PostStatus.CANCELLED);
                postRepository.save(post);
            }
            case POST -> {
                Post post = item.getPost();
                post.setStatus(PostStatus.CANCELLED);
                postRepository.save(post);
            }
            case CONVERSATION -> {
                // Closes the chat for both sides, recorded as closed by the reporter - so the
                // reported person also can't open a new anonymous chat with them.
                var conversation = item.getConversation();
                conversation.setClosedAt(Instant.now());
                conversation.setClosedBy(item.getReporter());
            }
        }
    }

    private ModerationItem requireItem(UUID id) {
        return moderationItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Moderation item not found: " + id));
    }

    private ModerationItemResponse toResponse(ModerationItem item) {
        String reporterName = item.getReporter() == null ? null : item.getReporter().getName();
        return switch (item.getContentType()) {
            case ROOM -> {
                Room room = item.getRoom();
                yield new ModerationItemResponse(item.getId().toString(), item.getContentType().wireValue(),
                        null, room.getPost().getBody(), null, room.getId().toString(), reporterName,
                        item.getReason(), item.getStatus().wireValue(), item.getCreatedAt().toString(), null);
            }
            case POST -> {
                Post post = item.getPost();
                yield new ModerationItemResponse(item.getId().toString(), item.getContentType().wireValue(),
                        null, post.getBody(), null, null, reporterName,
                        item.getReason(), item.getStatus().wireValue(), item.getCreatedAt().toString(), post.getId().toString());
            }
            case CONVERSATION -> {
                // Admins see the real accounts and the latest messages - anonymity is a display
                // rule for participants, not for moderation.
                var c = item.getConversation();
                var recent = threadMessageRepository.findTop100ByConversationIdOrderByCreatedAtDesc(c.getId()).stream()
                        .limit(6)
                        .map(m -> m.getSender().getName() + ": " + m.getContent())
                        .toList();
                String summary = "Chat between " + c.getUserA().getName() + " and " + c.getUserB().getName()
                        + (recent.isEmpty() ? "" : " - latest: " + String.join(" | ", recent.reversed()));
                yield new ModerationItemResponse(item.getId().toString(), item.getContentType().wireValue(),
                        null, summary, null, null, reporterName,
                        item.getReason(), item.getStatus().wireValue(), item.getCreatedAt().toString(), null);
            }
            case JOB_POSTING -> {
                JobPosting p = item.getJobPosting();
                yield new ModerationItemResponse(item.getId().toString(), item.getContentType().wireValue(),
                        p.getId().toString(), p.getTitle(), p.getEnterprise().getCompanyName(), null, null,
                        item.getReason(), item.getStatus().wireValue(), item.getCreatedAt().toString(), null);
            }
        };
    }
}
