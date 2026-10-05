package com.vikisol.arena.activities.service;

import com.vikisol.arena.audit.AuditActions;

import com.vikisol.arena.activities.ActivityRules;
import com.vikisol.arena.activities.entity.ActivityAttendance;
import com.vikisol.arena.activities.entity.DisputeStatus;
import com.vikisol.arena.activities.repository.ActivityAttendanceRepository;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.notifications.service.NotificationService;
import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.entity.PostJoinOutcome;
import com.vikisol.arena.posts.entity.PostJoinRequest;
import com.vikisol.arena.posts.repository.PostJoinRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

// ARENA-APP-FLOW §9 "Disputes: attendance (72h)". An Arena admin reviews a disputed no-show:
// accept (they're marked present, as if the host had accepted) or reject with a reason (the
// no-show stays and is final). Only the admin, the host and that person ever see any of it.
@Service
@RequiredArgsConstructor
public class AdminDisputeService {

    private final ActivityAttendanceRepository attendanceRepository;
    private final PostJoinRequestRepository joinRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final com.vikisol.arena.audit.AuditService auditService;

    public record DisputeView(String attendanceId, String joinId, String postId, String activity, String hostId, String hostName,
                              String userId, String name, String outcome, String hostCheckedInAt, Boolean joinerAttended,
                              String disputeReason, String disputedAt, String disputeOpenUntil, String status,
                              String resolutionNote, String resolvedAt,
                              // FE-API-GAPS row 46: the same dispute in the admin screen's terms.
                              String id, String activityTitle, String joinerName, String openedAt, String deadlineAt,
                              String state, String note) {
    }

    // Row 46: Arena's team answers a dispute within 72 hours of it being opened.
    static final java.time.Duration REVIEW_SLA = java.time.Duration.ofHours(72);

    // `status` takes the original values (open, accepted, rejected) and row 46's: resolved_joiner
    // (= accepted), resolved_host (= rejected), and expired (open past the 72-hour review SLA).
    // "open" lists every open dispute, overdue ones included.
    @Transactional(readOnly = true)
    public Page<DisputeView> queue(String status, Pageable pageable) {
        String wire = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        Instant slaStart = Instant.now().minus(REVIEW_SLA);
        switch (wire) {
            case "resolved_joiner" -> status = "accepted";
            case "resolved_host" -> status = "rejected";
            case "expired" -> {
                return attendanceRepository.findByDisputeStatusAndDisputedAtBeforeOrderByDisputedAtAscIdAsc(DisputeStatus.OPEN, slaStart, pageable)
                        .map(AdminDisputeService::view);
            }
            default -> {
            }
        }
        DisputeStatus s;
        try {
            s = status == null || status.isBlank() ? DisputeStatus.OPEN : DisputeStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(STATUSES);
        }
        if (s == DisputeStatus.NONE) throw new BadRequestException(STATUSES);
        return attendanceRepository.findByDisputeStatusOrderByDisputedAtAscIdAsc(s, pageable).map(AdminDisputeService::view);
    }

    private static final String STATUSES = "status must be one of open, expired, resolved_host, resolved_joiner (or accepted, rejected)";

    @Transactional
    public DisputeView decide(UUID adminId, UUID attendanceId, boolean accept, String note) {
        ActivityAttendance a = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute not found: " + attendanceId));
        if (a.getDisputeStatus() != DisputeStatus.OPEN) throw new BadRequestException("This dispute has already been decided");
        if (!accept && (note == null || note.isBlank())) throw new BadRequestException("Say why the no-show stands");
        PostJoinRequest join = a.getJoinRequest();
        a.setDisputeStatus(accept ? DisputeStatus.ACCEPTED : DisputeStatus.REJECTED);
        a.setDisputeResolvedAt(Instant.now());
        a.setDisputeResolvedBy(userRepository.getReferenceById(adminId));
        a.setDisputeResolutionNote(note == null || note.isBlank() ? null : note.trim());
        attendanceRepository.save(a);
        Post post = join.getPost();
        String title = post.getTitle() != null ? post.getTitle() : post.getBody();
        auditService.record(null, adminId, AuditActions.DISPUTE_RESOLVED,
                "dispute " + a.getId() + " (" + (accept ? "joiner" : "host") + ")", note == null || note.isBlank() ? "no reason given" : note.trim());
        if (accept) {
            join.setOutcome(PostJoinOutcome.ATTENDED);
            joinRepository.save(join);
            notificationService.notifySystem(join.getUser(), NotificationService.SAFETY, "Dispute accepted",
                    "Arena's team reviewed your dispute: you're marked present for \"" + preview(title) + "\".");
            notificationService.notifySystem(post.getAuthorUser(), NotificationService.SAFETY, "Attendance updated",
                    "Arena's team reviewed a dispute and marked " + join.getUser().getName() + " present.", join.getUser());
        } else {
            notificationService.notifySystem(join.getUser(), NotificationService.SAFETY, "Dispute not accepted",
                    "Arena's team reviewed your dispute about \"" + preview(title) + "\": " + note.trim());
        }
        return view(a);
    }

    private static String preview(String s) {
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }

    private static DisputeView view(ActivityAttendance a) {
        PostJoinRequest j = a.getJoinRequest();
        Post p = j.getPost();
        return new DisputeView(a.getId().toString(), j.getId().toString(), p.getId().toString(),
                preview(p.getTitle() != null ? p.getTitle() : p.getBody()), p.getAuthorUser().getId().toString(), p.getAuthorUser().getName(),
                j.getUser().getId().toString(), j.getUser().getName(), j.getOutcome() == null ? null : j.getOutcome().wireValue(),
                a.getCheckedInAt() == null ? null : a.getCheckedInAt().toString(), a.getJoinerAttended(), a.getDisputeReason(),
                a.getDisputedAt() == null ? null : a.getDisputedAt().toString(),
                a.getOutcomeRecordedAt() == null ? null : a.getOutcomeRecordedAt().plus(ActivityRules.DISPUTE_WINDOW).toString(),
                a.getDisputeStatus().wireValue(), a.getDisputeResolutionNote(),
                a.getDisputeResolvedAt() == null ? null : a.getDisputeResolvedAt().toString(),
                a.getId().toString(), preview(p.getTitle() != null ? p.getTitle() : p.getBody()), j.getUser().getName(),
                a.getDisputedAt() == null ? null : a.getDisputedAt().toString(),
                a.getDisputedAt() == null ? null : a.getDisputedAt().plus(REVIEW_SLA).toString(),
                state(a), a.getDisputeReason());
    }

    // Row 46's status words.
    private static String state(ActivityAttendance a) {
        return switch (a.getDisputeStatus()) {
            case ACCEPTED -> "resolved_joiner";
            case REJECTED -> "resolved_host";
            case OPEN -> a.getDisputedAt() != null && a.getDisputedAt().plus(REVIEW_SLA).isBefore(Instant.now()) ? "expired" : "open";
            default -> a.getDisputeStatus().wireValue();
        };
    }
}
