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
 * PA4 (moderation queue). Two sources: autoFlag() (banned-phrase scan at job-posting-creation
 * time) and fileRoomReport() (a real user report on a Room, ARENA-V2-PRODUCT-ARCHITECTURE.md
 * §4 - called from RoomService.report()). Depends on PostRepository directly (not PostService)
 * deliberately - PostService already depends on RoomService for room creation, so going through
 * PostService here (RoomService -> ModerationService -> PostService -> RoomService) would be a
 * circular bean dependency. Flipping a Post's status directly for the one case this needs
 * (ROOM-type takedown) is a small enough duplication to avoid that entirely.
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
        // ROOM-type items have no tenant (candidate-to-candidate, not enterprise-scoped) - no
        // audit call, same "nothing to audit" treatment ConversationService.sendMessage already
        // gives a candidate-sent message with no resolvable tenant.
    }

    @Transactional
    public void takedown(UUID actorUserId, UUID itemId) {
        ModerationItem item = requireItem(itemId);
        item.setStatus(ModerationStatus.TAKEN_DOWN);
        item.setResolvedBy(userRepository.getReferenceById(actorUserId));
        item.setResolvedAt(Instant.now());
        moderationItemRepository.save(item);

        if (item.getContentType() == ModerationContentType.JOB_POSTING) {
            JobPosting posting = item.getJobPosting();
            posting.setStatus(PostingStatus.CLOSED);
            jobPostingRepository.save(posting);
            auditService.record(posting.getEnterprise().getId(), actorUserId, AuditActions.MODERATION_TAKEDOWN, posting.getTitle());
        } else {
            // Cancels the underlying Post directly (not via PostService - see this class's own
            // header comment on why, to avoid a circular bean dependency). Doesn't notify room
            // members the way an author-initiated cancel does (RoomService.
            // notifyRoomOfCancellation) - a deliberate, smaller scope for this rarer admin path;
            // the room's history and membership stay intact, it just stops accepting new joins.
            Room room = item.getRoom();
            Post post = room.getPost();
            post.setStatus(PostStatus.CANCELLED);
            postRepository.save(post);
        }
    }

    private ModerationItem requireItem(UUID id) {
        return moderationItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Moderation item not found: " + id));
    }

    private ModerationItemResponse toResponse(ModerationItem item) {
        if (item.getContentType() == ModerationContentType.ROOM) {
            Room room = item.getRoom();
            String reporterName = item.getReporter() == null ? null : item.getReporter().getName();
            return new ModerationItemResponse(item.getId().toString(), item.getContentType().wireValue(),
                    null, room.getPost().getBody(), null, room.getId().toString(), reporterName,
                    item.getReason(), item.getStatus().wireValue(), item.getCreatedAt().toString());
        }
        JobPosting p = item.getJobPosting();
        return new ModerationItemResponse(item.getId().toString(), item.getContentType().wireValue(),
                p.getId().toString(), p.getTitle(), p.getEnterprise().getCompanyName(), null, null,
                item.getReason(), item.getStatus().wireValue(), item.getCreatedAt().toString());
    }
}
