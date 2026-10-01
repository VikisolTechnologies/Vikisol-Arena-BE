package com.vikisol.arena.enterprise.service;

import com.vikisol.arena.audit.AuditActions;
import com.vikisol.arena.audit.AuditService;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.enterprise.dto.CreatePostingRequest;
import com.vikisol.arena.enterprise.dto.JobPostingResponse;
import com.vikisol.arena.enterprise.dto.UpdatePostingRequest;
import com.vikisol.arena.hiring.dto.HiringDtos;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.entity.Plan;
import com.vikisol.arena.jobs.entity.EmploymentType;
import com.vikisol.arena.jobs.entity.JobPosting;
import com.vikisol.arena.jobs.entity.PostingStatus;
import com.vikisol.arena.jobs.repository.JobPostingRepository;
import com.vikisol.arena.platform.service.ModerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobPostingService {

    private final com.vikisol.arena.profile.industry.IndustryCatalogue industryCatalogue;
    private final JobPostingRepository jobPostingRepository;
    private final EnterpriseProfileService enterpriseProfileService;
    private final AuditService auditService;
    private final JobPostingMapper mapper;
    private final ModerationService moderationService;
    private final com.vikisol.arena.hiring.service.HiringService hiringService;
    private final com.vikisol.arena.platform.service.FeatureFlagService featureFlagService;
    private final com.vikisol.arena.business.repository.BusinessVerificationRepository verificationRepository;
    private final com.vikisol.arena.hiring.repository.JobRequirementRepository requirementRepository;
    private final com.vikisol.arena.applications.repository.ApplicationRepository applicationRepository;

    @Transactional(readOnly = true)
    public PagedResponse<JobPostingResponse> getMyPostings(UUID userId, Pageable pageable) {
        EnterpriseProfile enterprise = requireEnterprise(userId);
        var page = jobPostingRepository.findByEnterprise(enterprise, pageable);
        // One query for every posting's skills across the page (instead of one query per posting)
        // - see JobPostingRepository.findByIdInFetchingSkills for why this is a separate batched
        // call rather than an @EntityGraph on findByEnterprise itself.
        List<UUID> jobIds = page.getContent().stream().map(JobPosting::getId).toList();
        if (!jobIds.isEmpty()) {
            jobPostingRepository.findByIdInFetchingSkills(jobIds);
        }
        return PagedResponse.of(page, mapper::toResponse);
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
        PostingStatus status = request.status() == null || request.status().isBlank() ? PostingStatus.OPEN : PostingStatus.fromWireValue(request.status());
        if (status != PostingStatus.OPEN && status != PostingStatus.DRAFT) throw new BadRequestException("A new posting is a draft or open");
        if (status == PostingStatus.OPEN) {
            requirePublishAllowed(enterprise);
            requireRoomUnderCap(enterprise);
        }
        if (request.salaryMin() > request.salaryMax()) throw new BadRequestException("The minimum pay can't be more than the maximum");

        JobPosting posting = JobPosting.builder()
                .enterprise(enterprise)
                .title(request.title())
                .industry(industryCatalogue.resolveForWrite(request.industry(), null))
                .location(request.location())
                .remote(request.remote())
                .employmentType(EmploymentType.fromWireValue(request.employmentType()))
                .salaryMin(request.salaryMin())
                .salaryMax(request.salaryMax())
                .skills(request.skills())
                .description(request.description())
                .status(status)
                .experienceLevel(experienceLevel(request.experienceLevel()))
                .deadline(deadline(request.deadline()))
                .build();
        if (request.workMode() != null) setWorkMode(posting, request.workMode());
        JobPosting saved = jobPostingRepository.save(posting);
        if (request.mustHaves() != null || request.niceToHaves() != null) {
            hiringService.setRequirements(userId, saved.getId(), new HiringDtos.RequirementsRequest(
                    request.mustHaves() == null ? List.of() : request.mustHaves(), request.niceToHaves()));
        }
        if (request.questions() != null) hiringService.setScreening(userId, saved.getId(), request.questions());
        auditService.record(enterprise.getId(), userId, AuditActions.POSTING_CREATED, saved.getTitle());
        moderationService.autoFlag(saved);
        return mapper.toResponse(saved);
    }

    // Row 28: edit a draft or a live posting in place, instead of close-and-repost.
    @Transactional
    public JobPostingResponse updatePosting(UUID userId, UUID postingId, UpdatePostingRequest r) {
        JobPosting posting = requirePosting(postingId);
        EnterpriseProfile actingTenant = requireEnterprise(userId);
        if (!posting.getEnterprise().getId().equals(actingTenant.getId())) {
            throw new AccessDeniedException("Not your posting");
        }
        if (posting.getStatus() == PostingStatus.CLOSED) throw new BadRequestException("This posting is closed. Reopen it to edit it.");
        if (r.title() != null) {
            if (r.title().isBlank()) throw new BadRequestException("title can't be empty");
            posting.setTitle(r.title().trim());
        }
        if (r.industry() != null) posting.setIndustry(industryCatalogue.resolveForWrite(r.industry(), posting.getIndustry()));
        if (r.location() != null) {
            if (r.location().isBlank()) throw new BadRequestException("location can't be empty");
            posting.setLocation(r.location().trim());
        }
        if (r.employmentType() != null) posting.setEmploymentType(EmploymentType.fromWireValue(r.employmentType()));
        if (r.workMode() != null) setWorkMode(posting, r.workMode());
        if (r.salaryMin() != null) posting.setSalaryMin(r.salaryMin());
        if (r.salaryMax() != null) posting.setSalaryMax(r.salaryMax());
        if (posting.getSalaryMin() > posting.getSalaryMax()) throw new BadRequestException("The minimum pay can't be more than the maximum");
        if (r.skills() != null) {
            posting.getSkills().clear();
            posting.getSkills().addAll(r.skills().stream().filter(x -> x != null && !x.isBlank()).map(String::trim).distinct().toList());
        }
        boolean descriptionChanged = false;
        if (r.description() != null) {
            if (r.description().isBlank()) throw new BadRequestException("description can't be empty");
            descriptionChanged = !r.description().equals(posting.getDescription());
            posting.setDescription(r.description());
        }
        if (r.experienceLevel() != null) posting.setExperienceLevel(r.experienceLevel().isBlank() ? null : experienceLevel(r.experienceLevel()));
        if (r.deadline() != null) posting.setDeadline(deadline(r.deadline()));
        JobPosting saved = jobPostingRepository.save(posting);
        if (r.mustHaves() != null || r.niceToHaves() != null) {
            List<String> must = r.mustHaves() != null ? r.mustHaves() : currentRequirements(postingId, com.vikisol.arena.hiring.entity.JobRequirement.Kind.MUST);
            List<String> nice = r.niceToHaves() != null ? r.niceToHaves() : currentRequirements(postingId, com.vikisol.arena.hiring.entity.JobRequirement.Kind.NICE);
            hiringService.setRequirements(userId, postingId, new HiringDtos.RequirementsRequest(must, nice));
        }
        if (r.questions() != null) hiringService.setScreening(userId, postingId, r.questions());
        if (descriptionChanged) moderationService.autoFlag(saved);
        auditService.record(actingTenant.getId(), userId, AuditActions.POSTING_UPDATED, saved.getTitle());
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
        // Row 28: back to draft only while nobody has applied; going live counts against the plan.
        if (status == PostingStatus.DRAFT && posting.getStatus() != PostingStatus.DRAFT
                && applicationRepository.existsByJobPostingId(posting.getId())) {
            throw new BadRequestException("People have applied, so this posting can't go back to draft. Pause or close it instead.");
        }
        boolean goingLive = (status == PostingStatus.OPEN || status == PostingStatus.PAUSED)
                && (posting.getStatus() == PostingStatus.DRAFT || posting.getStatus() == PostingStatus.CLOSED);
        if (goingLive) requireRoomUnderCap(actingTenant);
        // ARCHITECT-REVIEW-BE-1 blocker #5: only checked on DRAFT->OPEN/PAUSED, so an unverified
        // company could still CLOSED/PAUSED->OPEN a posting straight back to live. Verification
        // is required on every transition INTO open, not just the first one.
        if (status == PostingStatus.OPEN) {
            requirePublishAllowed(actingTenant);
        }
        posting.setStatus(status);
        jobPostingRepository.save(posting);
        if (status == PostingStatus.CLOSED) {
            auditService.record(actingTenant.getId(), userId, AuditActions.POSTING_CLOSED, posting.getTitle());
        }
    }

    // Plan-based active-posting cap - mirrors arena-web's POSTING_LIMITS (plan.ts) and the check
    // createPosting() does in enterprise.ts before AUDIT.md flagged it as ungated. "Active" = open
    // or paused; drafts and closed postings don't count.
    private void requireRoomUnderCap(EnterpriseProfile enterprise) {
        int limit = postingLimitFor(enterprise.getPlan());
        long activeCount = jobPostingRepository.countByEnterpriseAndStatusIn(enterprise, List.of(PostingStatus.OPEN, PostingStatus.PAUSED));
        if (activeCount >= limit) {
            throw new BadRequestException("Your " + enterprise.getPlan().wireValue() + " plan allows " + limit
                    + " active posting" + (limit == 1 ? "" : "s") + ".");
        }
    }

    // Flow §8 B2: "Jobs can be drafted but not published" until an Arena admin verifies the
    // company. Behind the company_verification_required feature flag (off unless an admin turns
    // it on), so companies that were hiring before verification existed aren't cut off by a deploy.
    // Companies already here when the flag goes on are grandfathered (verified-legacy, V39).
    public static final String VERIFICATION_FLAG = "company_verification_required";

    private void requirePublishAllowed(EnterpriseProfile tenant) {
        if (featureFlagService.isEnabled(VERIFICATION_FLAG) && tenant.getVerificationGrandfatheredAt() == null
                && !verificationRepository.findByTenantId(tenant.getId())
                .map(v -> v.getStatus() == com.vikisol.arena.business.entity.BusinessVerification.Status.VERIFIED).orElse(false)) {
            throw new BadRequestException("Your company needs to be verified before jobs go live. You can save this as a draft meanwhile.");
        }
    }

    private List<String> currentRequirements(UUID postingId, com.vikisol.arena.hiring.entity.JobRequirement.Kind kind) {
        return requirementRepository.findByPostingIdOrderByKindAscPositionAsc(postingId).stream()
                .filter(x -> x.getKind() == kind).map(com.vikisol.arena.hiring.entity.JobRequirement::getText).toList();
    }

    private static void setWorkMode(JobPosting posting, String value) {
        JobPosting.WorkMode mode;
        try {
            mode = JobPosting.WorkMode.valueOf(value.trim().toUpperCase().replace("-", "").replace("_", ""));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("workMode must be one of onsite, hybrid, remote");
        }
        posting.setWorkMode(mode);
        posting.setRemote(mode == JobPosting.WorkMode.REMOTE);
    }

    // Also takes the frontend's labels ("Entry level (0–2 years)", "Mid level…", "Senior…").
    private static JobPosting.ExperienceLevel experienceLevel(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (v.startsWith("entry")) return JobPosting.ExperienceLevel.ENTRY;
        if (v.startsWith("mid")) return JobPosting.ExperienceLevel.MID;
        if (v.startsWith("senior")) return JobPosting.ExperienceLevel.SENIOR;
        throw new BadRequestException("experienceLevel must be one of entry, mid, senior");
    }

    private static java.time.LocalDate deadline(String value) {
        if (value == null || value.isBlank()) return null;
        java.time.LocalDate d;
        try {
            d = java.time.LocalDate.parse(value.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new BadRequestException("deadline must be a date like 2026-10-31");
        }
        if (d.isBefore(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")))) throw new BadRequestException("The deadline can't be in the past");
        return d;
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
