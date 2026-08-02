package com.vikisol.arena.applications.service;

import com.vikisol.arena.applications.dto.ApplicationResponse;
import com.vikisol.arena.applications.entity.Application;
import com.vikisol.arena.applications.entity.ApplicationStage;
import com.vikisol.arena.applications.repository.ApplicationRepository;
import com.vikisol.arena.activity.entity.ActivityEventType;
import com.vikisol.arena.activity.service.ActivityService;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.jobs.entity.JobPosting;
import com.vikisol.arena.jobs.repository.JobPostingRepository;
import com.vikisol.arena.notifications.service.NotificationService;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final JobPostingRepository jobPostingRepository;
    private final ApplicationMapper mapper;
    private final NotificationService notificationService;
    private final ActivityService activityService;

    @Transactional(readOnly = true)
    public PagedResponse<ApplicationResponse> getMyApplications(UUID userId, Pageable pageable) {
        CandidateProfile candidate = candidateProfileForUser(userId);
        return PagedResponse.of(applicationRepository.findByCandidateId(candidate.getId(), pageable), mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public boolean hasAppliedTo(UUID userId, UUID jobId) {
        CandidateProfile candidate = candidateProfileForUser(userId);
        return applicationRepository.existsByCandidateIdAndJobPostingId(candidate.getId(), jobId);
    }

    @Transactional
    public ApplicationResponse applyToJob(UUID userId, UUID jobId) {
        CandidateProfile candidate = candidateProfileForUser(userId);
        var existing = applicationRepository.findByCandidateIdAndJobPostingId(candidate.getId(), jobId);
        if (existing.isPresent()) {
            return mapper.toResponse(existing.get());
        }
        JobPosting job = jobPostingRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));

        Application application = Application.builder()
                .candidate(candidate)
                .jobPosting(job)
                .stage(ApplicationStage.APPLIED)
                .appliedAt(Instant.now())
                .build();
        application = applicationRepository.save(application);

        notificationService.notifyApplicationSubmitted(candidate, job);
        activityService.log(candidate.getUser(), ActivityEventType.APPLIED, "Applied to " + job.getTitle(),
                "Submitted an application to " + job.getTitle() + " at " + job.getEnterprise().getCompanyName() + ".",
                job.getId(), "Matched your skills and consent settings allowed auto-apply or you applied directly.", true);
        return mapper.toResponse(application);
    }

    @Transactional
    public void withdraw(UUID userId, UUID applicationId) {
        CandidateProfile candidate = candidateProfileForUser(userId);
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        if (!application.getCandidate().getId().equals(candidate.getId())) {
            throw new AccessDeniedException("Not your application");
        }
        applicationRepository.delete(application);
    }

    @Transactional
    public ApplicationResponse advanceStageAsCandidate(UUID userId, UUID applicationId, ApplicationStage stage) {
        CandidateProfile candidate = candidateProfileForUser(userId);
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        if (!application.getCandidate().getId().equals(candidate.getId())) {
            throw new AccessDeniedException("Not your application");
        }
        application.setStage(stage);
        return mapper.toResponse(applicationRepository.save(application));
    }

    @Transactional
    public Application advanceStageAsEnterprise(UUID enterpriseUserId, UUID applicationId, ApplicationStage stage) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Applicant not found: " + applicationId));
        if (!application.getJobPosting().getEnterprise().getUser().getId().equals(enterpriseUserId)) {
            throw new AccessDeniedException("Not your posting");
        }
        application.setStage(stage);
        Application saved = applicationRepository.save(application);
        notificationService.notifyStageChanged(saved);
        return saved;
    }

    private CandidateProfile candidateProfileForUser(UUID userId) {
        return candidateProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("No candidate profile for this account"));
    }
}
