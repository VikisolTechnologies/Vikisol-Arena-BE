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
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.service.EnterpriseProfileService;
import com.vikisol.arena.integration.provider.EmailMessage;
import com.vikisol.arena.integration.provider.EmailProvider;
import com.vikisol.arena.jobs.entity.JobPosting;
import com.vikisol.arena.jobs.repository.JobPostingRepository;
import com.vikisol.arena.notifications.service.NotificationService;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final JobPostingRepository jobPostingRepository;
    private final EnterpriseProfileService enterpriseProfileService;
    private final ApplicationMapper mapper;
    private final NotificationService notificationService;
    private final ActivityService activityService;
    private final EmailProvider emailProvider;

    // WhatsApp isn't wired at this call site (even though a stage change is naturally a WhatsApp
    // moment too) - Arena's domain model has no phone-number field anywhere yet (User/
    // CandidateProfile/EnterpriseProfile), so there's no real "to" to send to without fabricating
    // data. WhatsAppProvider/NoopWhatsAppProvider/WhatsAppBusinessProvider are fully built
    // (see integration/provider/) and ready to wire in here the same way EmailProvider is below,
    // once a phone field exists on CandidateProfile and a BSP is chosen.

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
        EnterpriseProfile actingTenant = enterpriseProfileService.getEntityForUser(enterpriseUserId);
        // Tenant comparison, not founding-admin comparison - any recruiter/company_admin on the
        // posting's tenant can move its applicants, not just whoever created it.
        if (!application.getJobPosting().getEnterprise().getId().equals(actingTenant.getId())) {
            throw new AccessDeniedException("Not your posting");
        }
        application.setStage(stage);
        Application saved = applicationRepository.save(application);
        notificationService.notifyStageChanged(saved);

        // Best-effort - a notification failure must never fail the stage transition itself (same
        // resilience contract as the welcome email in AuthService/meeting-link creation in
        // InterviewService).
        try {
            JobPosting job = saved.getJobPosting();
            emailProvider.sendEmail(EmailMessage.to(
                    saved.getCandidate().getUser().getEmail(),
                    "Your application to " + job.getTitle() + " has moved to " + saved.getStage().wireValue(),
                    "<p>Hi " + saved.getCandidate().getName() + ",</p><p>Your application to <b>" + job.getTitle()
                            + "</b> at " + job.getEnterprise().getCompanyName() + " has moved to <b>"
                            + saved.getStage().wireValue() + "</b>.</p><p>- The Vikisol Arena team</p>"));
        } catch (Exception e) {
            log.warn("Stage-change email failed for application {}: {}", saved.getId(), e.getMessage());
        }

        return saved;
    }

    private CandidateProfile candidateProfileForUser(UUID userId) {
        return candidateProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("No candidate profile for this account"));
    }
}
