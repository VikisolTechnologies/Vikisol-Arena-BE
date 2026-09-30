package com.vikisol.arena.applications.service;

import com.vikisol.arena.applications.dto.ApplicationNoteView;
import com.vikisol.arena.applications.dto.ApplicationTimeline;
import com.vikisol.arena.applications.dto.ApplyRequest;
import com.vikisol.arena.applications.entity.ApplicationEvent;
import com.vikisol.arena.applications.entity.ApplicationNote;
import com.vikisol.arena.applications.repository.ApplicationEventRepository;
import com.vikisol.arena.applications.repository.ApplicationNoteRepository;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.hiring.dto.HiringDtos;
import com.vikisol.arena.hiring.service.HiringService;
import java.util.List;
import com.vikisol.arena.applications.dto.ApplicationResponse;
import com.vikisol.arena.applications.entity.Application;
import com.vikisol.arena.applications.entity.ApplicationStage;
import com.vikisol.arena.applications.repository.ApplicationRepository;
import com.vikisol.arena.activity.entity.ActivityEventType;
import com.vikisol.arena.activity.service.ActivityService;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.audit.AuditActions;
import com.vikisol.arena.audit.AuditService;
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
    private final AuditService auditService;
    private final ApplicationMapper mapper;
    private final NotificationService notificationService;
    private final ActivityService activityService;
    private final EmailProvider emailProvider;
    private final ApplicationEventRepository eventRepository;
    private final ApplicationNoteRepository noteRepository;
    private final UserRepository userRepository;
    private final HiringService hiringService;

    // Flow §8 "close the loop": "Not selected" always carries a kind message.
    static final String KIND_NOT_SELECTED = "Thank you for applying and for the time you gave us. We've decided not to move forward"
            + " this time. We'd be glad to see you apply again.";

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
        // A withdrawn application doesn't count: the person can apply again.
        return applicationRepository.findByCandidateIdAndJobPostingId(candidate.getId(), jobId)
                .filter(a -> a.getStage() != ApplicationStage.WITHDRAWN).isPresent();
    }

    @Transactional
    public ApplicationResponse applyToJob(UUID userId, UUID jobId) {
        return applyToJob(userId, jobId, null, null, null);
    }

    // FE-API-GAPS row 20: answers, cover note and CTC sharing ride along with the application.
    // Applying again after withdrawing reopens the same application.
    @Transactional
    public ApplicationResponse applyToJob(UUID userId, UUID jobId, List<ApplyRequest.Answer> answers, String coverNote, Boolean includeCtc) {
        CandidateProfile candidate = candidateProfileForUser(userId);
        var existing = applicationRepository.findByCandidateIdAndJobPostingId(candidate.getId(), jobId);
        if (existing.isPresent() && existing.get().getStage() != ApplicationStage.WITHDRAWN) {
            return mapper.toResponse(existing.get());
        }
        JobPosting job = jobPostingRepository.findById(jobId)
                .filter(j -> j.getStatus() != com.vikisol.arena.jobs.entity.PostingStatus.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));
        // Only an open job takes applications, and not after its deadline (row 28).
        if (job.getStatus() != com.vikisol.arena.jobs.entity.PostingStatus.OPEN) {
            throw new BadRequestException("This job isn't taking applications right now");
        }
        if (job.getDeadline() != null && java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).isAfter(job.getDeadline())) {
            throw new BadRequestException("Applications for this job closed on " + job.getDeadline());
        }

        List<HiringDtos.AnswerInput> answerInputs = answers == null ? List.of()
                : answers.stream().map(a -> new HiringDtos.AnswerInput(a.questionId(), a.value())).toList();
        hiringService.checkAnswers(jobId, answerInputs);

        Application application = existing.orElseGet(() -> Application.builder().candidate(candidate).jobPosting(job).build());
        application.setStage(ApplicationStage.APPLIED);
        application.setAppliedAt(Instant.now());
        application.setCoverNote(coverNote == null || coverNote.isBlank() ? null : coverNote.trim());
        application.setIncludeCtc(Boolean.TRUE.equals(includeCtc));
        application = applicationRepository.save(application);
        record(application, ApplicationEvent.Type.APPLIED, ApplicationStage.APPLIED, null, null);
        if (!answerInputs.isEmpty()) {
            hiringService.saveScreening(userId, application.getId(), new HiringDtos.ScreeningAnswersRequest(answerInputs, null));
        }

        notificationService.notifyApplicationSubmitted(candidate, job);
        activityService.log(candidate.getUser(), ActivityEventType.APPLIED, "Applied to " + job.getTitle(),
                "Submitted an application to " + job.getTitle() + " at " + job.getEnterprise().getCompanyName() + ".",
                job.getId(), "Matched your skills and consent settings allowed auto-apply or you applied directly.", true);
        return mapper.toResponse(application);
    }

    // DELETE /applications/{id}: withdraw. Kept as a record (WITHDRAWN) rather than deleted, so
    // the company's pipeline and the candidate's tracker both show what happened.
    @Transactional
    public void withdraw(UUID userId, UUID applicationId) {
        Application application = requireOwn(userId, applicationId);
        if (application.getStage() == ApplicationStage.WITHDRAWN || application.getStage() == ApplicationStage.REJECTED) return;
        application.setStage(ApplicationStage.WITHDRAWN);
        applicationRepository.save(application);
        record(application, ApplicationEvent.Type.WITHDRAWN, ApplicationStage.WITHDRAWN, null, null);
        notifyCompany(application, "Application withdrawn", application.getCandidate().getName() + " withdrew from " + application.getJobPosting().getTitle() + ".");
    }

    // Architect item 2: a candidate may only withdraw. Every other stage is the company's.
    @Transactional
    public ApplicationResponse advanceStageAsCandidate(UUID userId, UUID applicationId, ApplicationStage stage) {
        if (stage != ApplicationStage.WITHDRAWN) {
            throw new AccessDeniedException("Only the company can move your application. You can withdraw it.");
        }
        withdraw(userId, applicationId);
        return mapper.toResponse(requireOwn(userId, applicationId));
    }

    // FE-API-GAPS row 21: the candidate answers an offer.
    @Transactional
    public ApplicationResponse decideOffer(UUID userId, UUID applicationId, boolean accept) {
        Application application = requireOwn(userId, applicationId);
        if (application.getStage() != ApplicationStage.OFFER) throw new BadRequestException("There's no offer to answer on this application");
        application.setStage(accept ? ApplicationStage.HIRED : ApplicationStage.WITHDRAWN);
        applicationRepository.save(application);
        record(application, accept ? ApplicationEvent.Type.OFFER_ACCEPTED : ApplicationEvent.Type.OFFER_DECLINED, application.getStage(), null, null);
        notifyCompany(application, accept ? "Offer accepted" : "Offer declined",
                application.getCandidate().getName() + (accept ? " accepted" : " declined") + " the offer for " + application.getJobPosting().getTitle() + ".");
        return mapper.toResponse(application);
    }

    // Flow §6: a hire shows on the profile only if the person chooses.
    @Transactional
    public ApplicationResponse setShowOutcome(UUID userId, UUID applicationId, boolean show) {
        Application application = requireOwn(userId, applicationId);
        if (application.getStage() != ApplicationStage.HIRED) throw new BadRequestException("Only a hire can go on your profile");
        application.setShowOutcome(show);
        return mapper.toResponse(applicationRepository.save(application));
    }

    // The candidate's own tracker: what happened and the messages sent to them. No recruiter names.
    @Transactional(readOnly = true)
    public List<ApplicationTimeline> candidateTimeline(UUID userId, UUID applicationId) {
        requireOwn(userId, applicationId);
        return eventRepository.findByApplicationIdOrderByCreatedAtAscIdAsc(applicationId).stream()
                .map(e -> new ApplicationTimeline(e.getType().name().toLowerCase(), e.getStage() == null ? null : e.getStage().wireValue(),
                        null, e.getMessage(), e.getCreatedAt().toString()))
                .toList();
    }

    // The company's view (row 30): with who did it.
    @Transactional(readOnly = true)
    public List<ApplicationTimeline> companyTimeline(UUID enterpriseUserId, UUID applicationId) {
        Application application = requireCompanyApplication(enterpriseUserId, applicationId);
        List<ApplicationTimeline> out = new java.util.ArrayList<>();
        var events = eventRepository.findByApplicationIdOrderByCreatedAtAscIdAsc(applicationId);
        // Applications from before V29 have no "applied" event: the applied date stands in.
        if (events.stream().noneMatch(e -> e.getType() == ApplicationEvent.Type.APPLIED)) {
            out.add(new ApplicationTimeline("applied", ApplicationStage.APPLIED.wireValue(), null, null, application.getAppliedAt().toString()));
        }
        events.forEach(e -> out.add(new ApplicationTimeline(e.getType().name().toLowerCase(),
                e.getStage() == null ? null : e.getStage().wireValue(),
                e.getActor() == null ? null : e.getActor().getName(), e.getMessage(), e.getCreatedAt().toString())));
        return out;
    }

    @Transactional(readOnly = true)
    public List<ApplicationNoteView> notes(UUID enterpriseUserId, UUID applicationId) {
        requireCompanyApplication(enterpriseUserId, applicationId);
        return noteRepository.findByApplicationIdOrderByCreatedAtDescIdDesc(applicationId).stream()
                .map(n -> new ApplicationNoteView(n.getId().toString(), n.getText(), n.getAuthor().getName(), n.getCreatedAt().toString()))
                .toList();
    }

    @Transactional
    public List<ApplicationNoteView> addNote(UUID enterpriseUserId, UUID applicationId, String text) {
        Application application = requireCompanyApplication(enterpriseUserId, applicationId);
        noteRepository.save(ApplicationNote.builder().application(application)
                .author(userRepository.getReferenceById(enterpriseUserId)).text(text.trim()).build());
        return notes(enterpriseUserId, applicationId);
    }

    @Transactional
    public Application advanceStageAsEnterprise(UUID enterpriseUserId, UUID applicationId, ApplicationStage stage) {
        return advanceStageAsEnterprise(enterpriseUserId, applicationId, stage, null);
    }

    // Row 31: the company's message goes to the candidate with the change. "Not selected" always
    // carries a kind message - the company's own, or Arena's standard one.
    @Transactional
    public Application advanceStageAsEnterprise(UUID enterpriseUserId, UUID applicationId, ApplicationStage stage, String message) {
        if (stage == ApplicationStage.WITHDRAWN) throw new BadRequestException("Only the candidate can withdraw an application");
        String note = message == null || message.isBlank() ? null : message.trim();
        if (note == null && stage == ApplicationStage.REJECTED) note = KIND_NOT_SELECTED;
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
        record(saved, ApplicationEvent.Type.STAGE, stage, userRepository.getReferenceById(enterpriseUserId), note);
        notificationService.notifyStageChanged(saved);
        if (note != null) {
            notificationService.notifyJob(saved.getCandidate().getUser(), "A message from " + saved.getJobPosting().getEnterprise().getCompanyName(), note);
        }
        auditService.record(actingTenant.getId(), enterpriseUserId, AuditActions.STAGE_MOVED,
                saved.getCandidate().getName() + " on " + saved.getJobPosting().getTitle(), "stage: " + stage.wireValue());

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
                            + saved.getStage().wireValue() + "</b>.</p>"
                            + (note == null ? "" : "<p>" + escape(note) + "</p>")
                            + "<p>- The Vikisol Arena team</p>"));
        } catch (Exception e) {
            log.warn("Stage-change email failed for application {}", saved.getId());
        }

        return saved;
    }

    private void record(Application application, ApplicationEvent.Type type, ApplicationStage stage, User actor, String message) {
        eventRepository.save(ApplicationEvent.builder().application(application).type(type).stage(stage).actor(actor).message(message).build());
    }

    private void notifyCompany(Application application, String title, String body) {
        notificationService.notifyJob(application.getJobPosting().getEnterprise().getUser(), title, body);
    }

    private Application requireOwn(UUID userId, UUID applicationId) {
        CandidateProfile candidate = candidateProfileForUser(userId);
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        if (!application.getCandidate().getId().equals(candidate.getId())) throw new AccessDeniedException("Not your application");
        return application;
    }

    private Application requireCompanyApplication(UUID enterpriseUserId, UUID applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Applicant not found: " + applicationId));
        EnterpriseProfile tenant = enterpriseProfileService.getEntityForUser(enterpriseUserId);
        if (!application.getJobPosting().getEnterprise().getId().equals(tenant.getId())) throw new AccessDeniedException("Not your applicant");
        return application;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private CandidateProfile candidateProfileForUser(UUID userId) {
        return candidateProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new BadRequestException("No candidate profile for this account"));
    }
}
