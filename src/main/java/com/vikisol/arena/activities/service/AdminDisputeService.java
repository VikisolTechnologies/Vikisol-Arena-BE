package com.vikisol.arena.activities.service;

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

    public record DisputeView(String attendanceId, String joinId, String postId, String activity, String hostId, String hostName,
                              String userId, String name, String outcome, String hostCheckedInAt, Boolean joinerAttended,
                              String disputeReason, String disputedAt, String disputeOpenUntil, String status,
                              String resolutionNote, String resolvedAt) {
    }

    @Transactional(readOnly = true)
    public Page<DisputeView> queue(String status, Pageable pageable) {
        DisputeStatus s;
        try {
            s = status == null || status.isBlank() ? DisputeStatus.OPEN : DisputeStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("status must be one of open, accepted, rejected");
        }
        if (s == DisputeStatus.NONE) throw new BadRequestException("status must be one of open, accepted, rejected");
        return attendanceRepository.findByDisputeStatusOrderByDisputedAtAscIdAsc(s, pageable).map(AdminDisputeService::view);
    }

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
        if (accept) {
            join.setOutcome(PostJoinOutcome.ATTENDED);
            joinRepository.save(join);
            notificationService.notifySystem(join.getUser(), NotificationService.SAFETY, "Dispute accepted",
                    "Arena's team reviewed your dispute: you're marked present for \"" + preview(title) + "\".");
            notificationService.notifySystem(post.getAuthorUser(), NotificationService.SAFETY, "Attendance updated",
                    "Arena's team reviewed a dispute and marked " + join.getUser().getName() + " present.");
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
                a.getDisputeResolvedAt() == null ? null : a.getDisputeResolvedAt().toString());
    }
}
