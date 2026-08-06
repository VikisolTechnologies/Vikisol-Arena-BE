package com.vikisol.arena.interviews.service;

import com.vikisol.arena.activity.entity.ActivityEventType;
import com.vikisol.arena.activity.service.ActivityService;
import com.vikisol.arena.audit.AuditActions;
import com.vikisol.arena.audit.AuditService;
import com.vikisol.arena.applications.entity.Application;
import com.vikisol.arena.applications.entity.ApplicationStage;
import com.vikisol.arena.applications.repository.ApplicationRepository;
import com.vikisol.arena.applications.service.ApplicationService;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.service.EnterpriseProfileService;
import com.vikisol.arena.integration.provider.EmailMessage;
import com.vikisol.arena.integration.provider.EmailProvider;
import com.vikisol.arena.integration.provider.MeetingLinkProvider;
import com.vikisol.arena.interviews.dto.HiringManagerInterviewResponse;
import com.vikisol.arena.interviews.dto.InterviewFeedbackDto;
import com.vikisol.arena.interviews.dto.InterviewResponse;
import com.vikisol.arena.interviews.dto.InterviewSlotDto;
import com.vikisol.arena.interviews.dto.SubmitInterviewFeedbackRequest;
import com.vikisol.arena.interviews.entity.Interview;
import com.vikisol.arena.interviews.entity.InterviewFeedback;
import com.vikisol.arena.interviews.entity.InterviewRecommendation;
import com.vikisol.arena.interviews.entity.InterviewSlot;
import com.vikisol.arena.interviews.entity.InterviewStatus;
import com.vikisol.arena.interviews.repository.InterviewRepository;
import com.vikisol.arena.notifications.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationService applicationService;
    private final EnterpriseProfileService enterpriseProfileService;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final ActivityService activityService;
    private final MeetingLinkProvider meetingLinkProvider;
    private final EmailProvider emailProvider;

    // IDOR fix (found via the ARENA-SHIP-IT.md endpoint audit): this previously took no caller
    // identity at all - any authenticated user of any role could read any interview's slots/
    // notes/feedback by application id. Reuses the same assertParticipant() every mutating
    // interview endpoint already goes through.
    @Transactional(readOnly = true)
    public Optional<InterviewResponse> getForApplication(UUID userId, UUID applicationId) {
        Optional<Interview> interview = interviewRepository.findByApplicationId(applicationId);
        if (interview.isPresent()) {
            assertParticipant(userId, interview.get());
        }
        return interview.map(this::toResponse);
    }

    // HM1: "My interviews" - only ones specifically assigned to this hiring manager, never the
    // tenant's whole pipeline (that's what distinguishes hiring_manager from recruiter/
    // company_admin - see HM4, DECISIONS.md). Was an unbounded List - now paginated like every
    // other list endpoint in this codebase (see InterviewController for the page/size wiring),
    // and the underlying query fetches all four associations toHiringManagerResponse() reads in
    // one go instead of once per row - see InterviewRepository.findByAssignedHiringManagerId().
    @Transactional(readOnly = true)
    public PagedResponse<HiringManagerInterviewResponse> getMyAssignedInterviews(UUID hiringManagerUserId, Pageable pageable) {
        return PagedResponse.of(interviewRepository.findByAssignedHiringManagerId(hiringManagerUserId, pageable),
                this::toHiringManagerResponse);
    }

    @Transactional(readOnly = true)
    public HiringManagerInterviewResponse getMyAssignedInterview(UUID hiringManagerUserId, UUID interviewId) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found: " + interviewId));
        assertHiringManagerAssignmentIfApplicable(hiringManagerUserId, interview);
        if (interview.getAssignedHiringManager() == null || !interview.getAssignedHiringManager().getId().equals(hiringManagerUserId)) {
            throw new AccessDeniedException("This interview isn't assigned to you");
        }
        return toHiringManagerResponse(interview);
    }

    // HM3: a recruiter/company_admin assigns a hiring manager when scheduling an interview.
    @Transactional
    public void assignHiringManager(UUID actingUserId, UUID interviewId, UUID hiringManagerUserId) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found: " + interviewId));
        EnterpriseProfile actingTenant = enterpriseProfileService.getEntityForUser(actingUserId);
        if (!interview.getApplication().getJobPosting().getEnterprise().getId().equals(actingTenant.getId())) {
            throw new AccessDeniedException("Not your interview");
        }
        User hiringManager = userRepository.findById(hiringManagerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + hiringManagerUserId));
        if (hiringManager.getRole() != Role.HIRING_MANAGER) {
            throw new com.vikisol.arena.common.exception.BadRequestException("That person isn't a hiring manager");
        }
        EnterpriseProfile hiringManagerTenant = enterpriseProfileService.getEntityForUser(hiringManagerUserId);
        if (!hiringManagerTenant.getId().equals(actingTenant.getId())) {
            throw new com.vikisol.arena.common.exception.BadRequestException("That hiring manager isn't on your team");
        }
        interview.setAssignedHiringManager(hiringManager);
        interviewRepository.save(interview);
    }

    @Transactional
    public InterviewResponse propose(UUID actingUserId, UUID applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        assertParticipant(actingUserId, application);

        Optional<Interview> existing = interviewRepository.findByApplicationId(applicationId);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        Interview interview = Interview.builder().application(application).status(InterviewStatus.PROPOSED).build();
        List<InterviewSlot> slots = threeSlotsFromNow(interview);
        interview.setProposedSlots(slots);
        interview = interviewRepository.save(interview);

        notificationService.notifyInterviewProposed(application);
        activityService.log(application.getCandidate().getUser(), ActivityEventType.INTERVIEW_PROPOSED,
                "Interview slots proposed", application.getJobPosting().getEnterprise().getCompanyName()
                        + " proposed 3 interview slots for " + application.getJobPosting().getTitle() + ".",
                application.getJobPosting().getId(), null, true);

        // propose() is reachable from either side of the application (assertParticipant covers
        // both) - only audit it when the caller is actually the enterprise side, matching the
        // spec's "recruiter/admin action" scope. Candidate-initiated proposals aren't audited.
        try {
            EnterpriseProfile actingTenant = enterpriseProfileService.getEntityForUser(actingUserId);
            auditService.record(actingTenant.getId(), actingUserId, AuditActions.INTERVIEW_SCHEDULED,
                    application.getCandidate().getName() + " for " + application.getJobPosting().getTitle());
        } catch (ResourceNotFoundException ignored) {
            // Candidate proposed it themselves (or a non-tenant user) - nothing to audit.
        }

        return toResponse(interview);
    }

    // Deliberately NOT @Transactional: this method spans two synchronous external HTTP calls
    // (meeting-link/calendar creation, then a confirmation email) that can together take up to
    // ~50s under a slow provider. Previously the whole method - DB reads/writes included - ran
    // inside one @Transactional block, holding a pooled DB connection checked out for that entire
    // span; under load with several confirmations in flight that starves the pool for every other
    // request. The DB phase below now runs to completion and commits (each JpaRepository call is
    // transactional on its own - see SimpleJpaRepository - exactly like every other bare
    // repository call already made outside an explicit @Transactional elsewhere in this codebase)
    // *before* either slow external call starts, so no connection sits idle-in-transaction while
    // waiting on Graph/Resend. Both external calls keep their existing try/catch: a provider
    // failure must never block the confirmation itself (same resilience contract as before).
    public InterviewResponse confirmSlot(UUID actingUserId, UUID interviewId, UUID slotId) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found: " + interviewId));
        assertParticipant(actingUserId, interview);

        InterviewSlot confirmedSlot = interview.getProposedSlots().stream()
                .filter(s -> s.getId().equals(slotId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found: " + slotId));

        interview.setConfirmedSlotId(slotId);
        interview.setStatus(InterviewStatus.CONFIRMED);
        interview = interviewRepository.save(interview);

        notificationService.notifyInterviewConfirmed(interview.getApplication());
        activityService.log(interview.getApplication().getCandidate().getUser(), ActivityEventType.INTERVIEW_CONFIRMED,
                "Interview confirmed", "Confirmed an interview slot for " + interview.getApplication().getJobPosting().getTitle() + ".",
                interview.getApplication().getJobPosting().getId(), null, false);

        // --- DB phase committed above; everything from here on is a slow external call (or
        // persisting its result) and intentionally runs with no open transaction. ---
        Application application = interview.getApplication();
        List<String> attendeeEmails = List.of(
                application.getCandidate().getUser().getEmail(),
                application.getJobPosting().getEnterprise().getUser().getEmail());
        try {
            String meetingLink = meetingLinkProvider.createMeetingLink(
                    interview.getId(),
                    application.getJobPosting().getTitle() + " Interview",
                    confirmedSlot.getStart(),
                    confirmedSlot.getDurationMinutes(),
                    attendeeEmails);
            interview.setMeetingLink(meetingLink);
            interview = interviewRepository.save(interview);
        } catch (Exception e) {
            log.warn("Meeting-link creation failed for interview {}: {}", interview.getId(), e.getMessage());
        }

        try {
            emailProvider.sendEmail(EmailMessage.to(
                    application.getCandidate().getUser().getEmail(),
                    "Interview confirmed - " + application.getJobPosting().getTitle(),
                    "<p>Hi " + application.getCandidate().getName() + ",</p><p>Your interview for <b>"
                            + application.getJobPosting().getTitle() + "</b> at " + application.getJobPosting().getEnterprise().getCompanyName()
                            + " is confirmed for " + confirmedSlot.getStart() + ".</p>"
                            + (interview.getMeetingLink() != null ? "<p>Join link: <a href=\"" + interview.getMeetingLink() + "\">" + interview.getMeetingLink() + "</a></p>" : "")
                            + "<p>- The Vikisol Arena team</p>"));
        } catch (Exception e) {
            log.warn("Interview-confirmation email failed for interview {}: {}", interview.getId(), e.getMessage());
        }

        return toResponse(interview);
    }

    // Notes are shared, editable state either side of the interview may write - matches
    // InterviewRoom.tsx, where the notes textarea isn't gated by canGiveFeedback, only the
    // "End & give feedback" action is. Reuses assertParticipant, same participant check
    // confirmSlot()/propose() already apply.
    @Transactional
    public InterviewResponse saveNotes(UUID actingUserId, UUID interviewId, String notes) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found: " + interviewId));
        assertParticipant(actingUserId, interview);

        interview.setNotes(notes);
        return toResponse(interviewRepository.save(interview));
    }

    // Enterprise-only (matches arena-web's canGiveFeedback, only ever true on the
    // /enterprise/interviews/[applicationId] route) - submitting feedback both completes the
    // interview and, in the same transaction, folds the recommendation into the application's
    // pipeline stage exactly like submitInterviewFeedback() does in interviews.ts:
    // advance -> offer, reject -> rejected, hold -> stays at interview. Reuses
    // ApplicationService.advanceStageAsEnterprise() rather than duplicating the ownership check /
    // notification / stage-change email it already does for ApplicantService.moveStage().
    @Transactional
    public InterviewResponse submitFeedback(UUID enterpriseUserId, UUID interviewId, SubmitInterviewFeedbackRequest request) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found: " + interviewId));
        Application application = interview.getApplication();
        EnterpriseProfile actingTenant = enterpriseProfileService.getEntityForUser(enterpriseUserId);
        if (!application.getJobPosting().getEnterprise().getId().equals(actingTenant.getId())) {
            throw new AccessDeniedException("Not your interview");
        }
        assertHiringManagerAssignmentIfApplicable(enterpriseUserId, interview);

        InterviewRecommendation recommendation = InterviewRecommendation.fromWireValue(request.recommendation());
        interview.setFeedback(InterviewFeedback.builder()
                .rating(request.rating())
                .strengths(request.strengths())
                .concerns(request.concerns())
                .recommendation(recommendation)
                .submittedAt(Instant.now())
                .build());
        interview.setStatus(InterviewStatus.COMPLETED);
        interview = interviewRepository.save(interview);
        auditService.record(actingTenant.getId(), enterpriseUserId, AuditActions.FEEDBACK_SUBMITTED,
                application.getCandidate().getName(), "recommendation: " + recommendation.wireValue());

        ApplicationStage nextStage = switch (recommendation) {
            case ADVANCE -> ApplicationStage.OFFER;
            case REJECT -> ApplicationStage.REJECTED;
            case HOLD -> ApplicationStage.INTERVIEW;
        };
        applicationService.advanceStageAsEnterprise(enterpriseUserId, application.getId(), nextStage);

        return toResponse(interview);
    }

    private void assertParticipant(UUID userId, Application application) {
        boolean isCandidate = application.getCandidate().getUser().getId().equals(userId);
        boolean isEnterprise = false;
        if (!isCandidate) {
            try {
                EnterpriseProfile actingTenant = enterpriseProfileService.getEntityForUser(userId);
                isEnterprise = application.getJobPosting().getEnterprise().getId().equals(actingTenant.getId());
            } catch (ResourceNotFoundException ignored) {
                // Not an enterprise user at all (e.g. hiring_manager with no tenant membership
                // resolved yet, or a stray candidate id) - isEnterprise stays false.
            }
        }
        if (!isCandidate && !isEnterprise) {
            throw new AccessDeniedException("Not part of this application");
        }
    }

    // Interview-level overload, used once an Interview row already exists (confirmSlot,
    // saveNotes) - unlike the Application-level overload above (only ever used by propose(),
    // which by definition runs before any hiring_manager assignment could exist), this one
    // additionally tightens HIRING_MANAGER access down to *their specific* assigned interview,
    // per HM4's isolation requirement (DECISIONS.md flagged this as deferred to this phase).
    // RECRUITER/COMPANY_ADMIN keep tenant-wide access, matching the rest of the workspace.
    private void assertParticipant(UUID userId, Interview interview) {
        assertParticipant(userId, interview.getApplication());
        assertHiringManagerAssignmentIfApplicable(userId, interview);
    }

    private void assertHiringManagerAssignmentIfApplicable(UUID userId, Interview interview) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getRole() != Role.HIRING_MANAGER) return;
        boolean isAssigned = interview.getAssignedHiringManager() != null
                && interview.getAssignedHiringManager().getId().equals(userId);
        if (!isAssigned) {
            throw new AccessDeniedException("This interview isn't assigned to you");
        }
    }

    private List<InterviewSlot> threeSlotsFromNow(Interview interview) {
        return List.of(1, 2, 3).stream()
                .map(days -> InterviewSlot.builder()
                        .interview(interview)
                        .start(Instant.now().plus(Duration.ofDays(days)).plus(Duration.ofHours(14)))
                        .durationMinutes(45)
                        .build())
                .toList();
    }

    private InterviewResponse toResponse(Interview i) {
        return new InterviewResponse(
                i.getId().toString(),
                i.getApplication().getId().toString(),
                i.getProposedSlots().stream()
                        .map(s -> new InterviewSlotDto(s.getId().toString(), s.getStart().toString(), s.getDurationMinutes()))
                        .toList(),
                i.getConfirmedSlotId() == null ? null : i.getConfirmedSlotId().toString(),
                i.getStatus().wireValue(),
                i.getMeetingLink(),
                i.getNotes(),
                toFeedbackDto(i.getFeedback())
        );
    }

    private HiringManagerInterviewResponse toHiringManagerResponse(Interview i) {
        Application application = i.getApplication();
        return new HiringManagerInterviewResponse(
                i.getId().toString(),
                application.getId().toString(),
                application.getCandidate().getName(),
                application.getCandidate().getAvatarEmoji(),
                application.getJobPosting().getTitle(),
                application.getJobPosting().getEnterprise().getCompanyName(),
                i.getProposedSlots().stream()
                        .map(s -> new InterviewSlotDto(s.getId().toString(), s.getStart().toString(), s.getDurationMinutes()))
                        .toList(),
                i.getConfirmedSlotId() == null ? null : i.getConfirmedSlotId().toString(),
                i.getStatus().wireValue(),
                i.getMeetingLink(),
                i.getNotes(),
                toFeedbackDto(i.getFeedback())
        );
    }

    private InterviewFeedbackDto toFeedbackDto(InterviewFeedback f) {
        if (f == null || f.getRecommendation() == null) {
            return null;
        }
        return new InterviewFeedbackDto(
                f.getRating() == null ? 0 : f.getRating(),
                f.getStrengths(),
                f.getConcerns(),
                f.getRecommendation().wireValue(),
                f.getSubmittedAt() == null ? null : f.getSubmittedAt().toString()
        );
    }
}
