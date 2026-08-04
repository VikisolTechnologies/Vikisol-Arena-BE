package com.vikisol.arena.platform.service;

import com.vikisol.arena.audit.AuditActions;
import com.vikisol.arena.audit.AuditService;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.jobs.entity.JobPosting;
import com.vikisol.arena.jobs.entity.PostingStatus;
import com.vikisol.arena.jobs.repository.JobPostingRepository;
import com.vikisol.arena.platform.dto.ModerationItemResponse;
import com.vikisol.arena.platform.entity.ModerationItem;
import com.vikisol.arena.platform.entity.ModerationStatus;
import com.vikisol.arena.platform.repository.ModerationItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * PA4 (moderation queue). There's no reporting UI on the candidate/recruiter side yet, so the
 * queue's only source today is autoFlag() below, called from JobPostingService.createPosting -
 * a small banned-phrase scan against title+description. Good enough to demo a working queue
 * without building a full reporting flow, which is out of scope for this sprint.
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
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional
    public void autoFlag(JobPosting posting) {
        String haystack = (posting.getTitle() + " " + posting.getDescription()).toLowerCase();
        List<String> matched = FLAGGED_PHRASES.stream().filter(haystack::contains).toList();
        if (matched.isEmpty()) return;
        moderationItemRepository.save(ModerationItem.builder()
                .jobPosting(posting)
                .reason("Flagged terms: " + String.join(", ", matched))
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
        auditService.record(item.getJobPosting().getEnterprise().getId(), actorUserId,
                AuditActions.MODERATION_DISMISSED, item.getJobPosting().getTitle());
    }

    @Transactional
    public void takedown(UUID actorUserId, UUID itemId) {
        ModerationItem item = requireItem(itemId);
        item.setStatus(ModerationStatus.TAKEN_DOWN);
        item.setResolvedBy(userRepository.getReferenceById(actorUserId));
        item.setResolvedAt(Instant.now());
        moderationItemRepository.save(item);

        JobPosting posting = item.getJobPosting();
        posting.setStatus(PostingStatus.CLOSED);
        jobPostingRepository.save(posting);

        auditService.record(posting.getEnterprise().getId(), actorUserId, AuditActions.MODERATION_TAKEDOWN, posting.getTitle());
    }

    private ModerationItem requireItem(UUID id) {
        return moderationItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Moderation item not found: " + id));
    }

    private ModerationItemResponse toResponse(ModerationItem item) {
        JobPosting p = item.getJobPosting();
        return new ModerationItemResponse(item.getId().toString(), p.getId().toString(), p.getTitle(),
                p.getEnterprise().getCompanyName(), item.getReason(), item.getStatus().wireValue(), item.getCreatedAt().toString());
    }
}
