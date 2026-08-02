package com.vikisol.arena.interviews.service;

import com.vikisol.arena.activity.entity.ActivityEventType;
import com.vikisol.arena.activity.service.ActivityService;
import com.vikisol.arena.applications.entity.Application;
import com.vikisol.arena.applications.repository.ApplicationRepository;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.interviews.dto.InterviewResponse;
import com.vikisol.arena.interviews.dto.InterviewSlotDto;
import com.vikisol.arena.interviews.entity.Interview;
import com.vikisol.arena.interviews.entity.InterviewSlot;
import com.vikisol.arena.interviews.entity.InterviewStatus;
import com.vikisol.arena.interviews.repository.InterviewRepository;
import com.vikisol.arena.notifications.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final ApplicationRepository applicationRepository;
    private final NotificationService notificationService;
    private final ActivityService activityService;

    @Transactional(readOnly = true)
    public Optional<InterviewResponse> getForApplication(UUID applicationId) {
        return interviewRepository.findByApplicationId(applicationId).map(this::toResponse);
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

        return toResponse(interview);
    }

    @Transactional
    public InterviewResponse confirmSlot(UUID actingUserId, UUID interviewId, UUID slotId) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found: " + interviewId));
        assertParticipant(actingUserId, interview.getApplication());

        boolean slotExists = interview.getProposedSlots().stream().anyMatch(s -> s.getId().equals(slotId));
        if (!slotExists) {
            throw new ResourceNotFoundException("Slot not found: " + slotId);
        }
        interview.setConfirmedSlotId(slotId);
        interview.setStatus(InterviewStatus.CONFIRMED);
        interview = interviewRepository.save(interview);

        notificationService.notifyInterviewConfirmed(interview.getApplication());
        activityService.log(interview.getApplication().getCandidate().getUser(), ActivityEventType.INTERVIEW_CONFIRMED,
                "Interview confirmed", "Confirmed an interview slot for " + interview.getApplication().getJobPosting().getTitle() + ".",
                interview.getApplication().getJobPosting().getId(), null, false);

        return toResponse(interview);
    }

    private void assertParticipant(UUID userId, Application application) {
        boolean isCandidate = application.getCandidate().getUser().getId().equals(userId);
        boolean isEnterprise = application.getJobPosting().getEnterprise().getUser().getId().equals(userId);
        if (!isCandidate && !isEnterprise) {
            throw new AccessDeniedException("Not part of this application");
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
                i.getStatus().wireValue()
        );
    }
}
