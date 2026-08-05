package com.vikisol.arena.enterprise.service;

import com.vikisol.arena.audit.AuditActions;
import com.vikisol.arena.audit.AuditService;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.enterprise.dto.CreatePostingRequest;
import com.vikisol.arena.enterprise.dto.JobPostingResponse;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.entity.Plan;
import com.vikisol.arena.jobs.entity.EmploymentType;
import com.vikisol.arena.jobs.entity.JobPosting;
import com.vikisol.arena.jobs.entity.PostingStatus;
import com.vikisol.arena.jobs.repository.JobPostingRepository;
import com.vikisol.arena.platform.service.ModerationService;
import com.vikisol.arena.profile.entity.Industry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobPostingService {

    private final JobPostingRepository jobPostingRepository;
    private final EnterpriseProfileService enterpriseProfileService;
    private final AuditService auditService;
    private final JobPostingMapper mapper;
    private final ModerationService moderationService;

    @Transactional(readOnly = true)
    public PagedResponse<JobPostingResponse> getMyPostings(UUID userId, Pageable pageable) {
        EnterpriseProfile enterprise = requireEnterprise(userId);
        return PagedResponse.of(jobPostingRepository.findByEnterprise(enterprise, pageable), mapper::toResponse);
    }

    // IDOR fix (found via the ARENA-SHIP-IT.md endpoint audit): this was previously a bare
    // findById with no tenant check at all - any recruiter could fetch any other tenant's
    // posting by guessing/enumerating its UUID. This endpoint is only reachable by
    // RECRUITER/COMPANY_ADMIN (JobPostingController's class-level @PreAuthorize), never
    // candidates, so the fix is the same tenant-id comparison setStatus() already uses.
    @Transactional(readOnly = true)
    public JobPostingResponse getPosting(UUID userId, UUID id) {
        JobPosting posting = requirePosting(id);
        EnterpriseProfile actingTenant = requireEnterprise(userId);
        if (!posting.getEnterprise().getId().equals(actingTenant.getId())) {
            throw new AccessDeniedException("Not your posting");
        }
        return mapper.toResponse(posting);
    }

    @Transactional
    public JobPostingResponse createPosting(UUID userId, CreatePostingRequest request) {
        EnterpriseProfile enterprise = requireEnterprise(userId);

        // Plan-based active-posting cap - mirrors arena-web's POSTING_LIMITS (plan.ts) and the
        // check createPosting() does in enterprise.ts before AUDIT.md flagged it as ungated.
        // "Active" = anything not closed (open or paused); closed postings don't count.
        int limit = postingLimitFor(enterprise.getPlan());
        long activeCount = jobPostingRepository.countByEnterpriseAndStatusNot(enterprise, PostingStatus.CLOSED);
        if (activeCount >= limit) {
            throw new BadRequestException("Your " + enterprise.getPlan().wireValue() + " plan allows " + limit
                    + " active posting" + (limit == 1 ? "" : "s") + ".");
        }

        JobPosting posting = JobPosting.builder()
                .enterprise(enterprise)
                .title(request.title())
                .industry(Industry.fromWireValue(request.industry()))
                .location(request.location())
                .remote(request.remote())
                .employmentType(EmploymentType.fromWireValue(request.employmentType()))
                .salaryMin(request.salaryMin())
                .salaryMax(request.salaryMax())
                .skills(request.skills())
                .description(request.description())
                .status(PostingStatus.OPEN)
                .build();
        JobPosting saved = jobPostingRepository.save(posting);
        auditService.record(enterprise.getId(), userId, AuditActions.POSTING_CREATED, saved.getTitle());
        moderationService.autoFlag(saved);
        return mapper.toResponse(saved);
    }

    @Transactional
    public void setStatus(UUID userId, UUID postingId, PostingStatus status) {
        JobPosting posting = requirePosting(postingId);
        EnterpriseProfile actingTenant = requireEnterprise(userId);
        // Compare tenants, not the founding-admin user - any recruiter/company_admin on the
        // same tenant manages any of that tenant's postings, not just ones they personally
        // created (see DECISIONS.md: EnterpriseProfile.user is no longer the ownership check).
        if (!posting.getEnterprise().getId().equals(actingTenant.getId())) {
            throw new AccessDeniedException("Not your posting");
        }
        posting.setStatus(status);
        jobPostingRepository.save(posting);
        if (status == PostingStatus.CLOSED) {
            auditService.record(actingTenant.getId(), userId, AuditActions.POSTING_CLOSED, posting.getTitle());
        }
    }

    // Mirrors arena-web's POSTING_LIMITS constant (plan.ts) exactly: free:1, pro:10,
    // enterprise:Infinity. Integer.MAX_VALUE stands in for Infinity since the count comparison
    // (`activeCount >= limit`) is never going to be reached by a real enterprise's posting volume.
    private int postingLimitFor(Plan plan) {
        return switch (plan) {
            case FREE -> 1;
            case PRO -> 10;
            case ENTERPRISE -> Integer.MAX_VALUE;
        };
    }

    private JobPosting requirePosting(UUID id) {
        return jobPostingRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Posting not found: " + id));
    }

    private EnterpriseProfile requireEnterprise(UUID userId) {
        return enterpriseProfileService.getEntityForUser(userId);
    }
}
