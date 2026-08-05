package com.vikisol.arena.enterprise.service;

import com.vikisol.arena.applications.entity.Application;
import com.vikisol.arena.applications.entity.ApplicationStage;
import com.vikisol.arena.applications.repository.ApplicationRepository;
import com.vikisol.arena.applications.service.ApplicationService;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.enterprise.dto.ApplicantResponse;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.jobs.entity.JobPosting;
import com.vikisol.arena.jobs.repository.JobPostingRepository;
import com.vikisol.arena.profile.service.CandidateProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Enterprise-side view over the same `Application` rows ApplicationService exposes to
 * candidates - see AUDIT.md item (a). No separate "Applicant" table/list is seeded or written
 * here; this only reads and reshapes what already exists.
 */
@Service
@RequiredArgsConstructor
public class ApplicantService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationService applicationService;
    private final EnterpriseProfileService enterpriseProfileService;
    private final CandidateProfileMapper candidateProfileMapper;
    private final JobPostingRepository jobPostingRepository;

    // IDOR fix (found via the ARENA-SHIP-IT.md endpoint audit): this previously took no caller
    // identity at all - any recruiter/company_admin could list another tenant's full applicant
    // roster by posting id. Fetching the posting first (not just checking applications) also
    // gives a real 404 for a genuinely nonexistent posting rather than an empty page either way.
    @Transactional(readOnly = true)
    public PagedResponse<ApplicantResponse> getApplicantsForPosting(UUID enterpriseUserId, UUID postingId, Pageable pageable) {
        JobPosting posting = jobPostingRepository.findById(postingId)
                .orElseThrow(() -> new ResourceNotFoundException("Posting not found: " + postingId));
        EnterpriseProfile actingTenant = enterpriseProfileService.getEntityForUser(enterpriseUserId);
        if (!posting.getEnterprise().getId().equals(actingTenant.getId())) {
            throw new AccessDeniedException("Not your posting");
        }
        return PagedResponse.of(applicationRepository.findByJobPostingId(postingId, pageable), this::toResponse);
    }

    // Fills a real gap: arena-web's enterprise/interviews/[applicationId] page needs to look up
    // a single application by id, but the only single-application-by-id endpoint that existed
    // (GET /applications, i.e. "my applications") is TALENT-only and always 403s for an
    // enterprise caller - that page has been silently broken in real mode since it was built.
    @Transactional(readOnly = true)
    public ApplicantResponse getApplicant(UUID enterpriseUserId, UUID applicantId) {
        Application application = applicationRepository.findById(applicantId)
                .orElseThrow(() -> new ResourceNotFoundException("Applicant not found: " + applicantId));
        EnterpriseProfile actingTenant = enterpriseProfileService.getEntityForUser(enterpriseUserId);
        if (!application.getJobPosting().getEnterprise().getId().equals(actingTenant.getId())) {
            throw new AccessDeniedException("Not your applicant");
        }
        return toResponse(application);
    }

    @Transactional
    public ApplicantResponse moveStage(UUID enterpriseUserId, UUID applicantId, ApplicationStage stage) {
        Application application = applicationService.advanceStageAsEnterprise(enterpriseUserId, applicantId, stage);
        return toResponse(application);
    }

    private ApplicantResponse toResponse(Application a) {
        return new ApplicantResponse(
                a.getId().toString(), a.getJobPosting().getId().toString(), a.getCandidate().getId().toString(),
                a.getStage().wireValue(), a.getAppliedAt().toString(), candidateProfileMapper.toResponse(a.getCandidate()));
    }
}
