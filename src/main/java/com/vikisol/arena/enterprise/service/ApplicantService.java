package com.vikisol.arena.enterprise.service;

import com.vikisol.arena.applications.entity.Application;
import com.vikisol.arena.applications.entity.ApplicationStage;
import com.vikisol.arena.applications.repository.ApplicationRepository;
import com.vikisol.arena.applications.service.ApplicationService;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.enterprise.dto.ApplicantResponse;
import com.vikisol.arena.profile.service.CandidateProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
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
    private final CandidateProfileMapper candidateProfileMapper;

    @Transactional(readOnly = true)
    public PagedResponse<ApplicantResponse> getApplicantsForPosting(UUID postingId, Pageable pageable) {
        return PagedResponse.of(applicationRepository.findByJobPostingId(postingId, pageable), this::toResponse);
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
