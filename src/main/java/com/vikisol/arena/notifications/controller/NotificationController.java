package com.vikisol.arena.notifications.controller;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.dto.PageLimits;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.notifications.dto.NotificationResponse;
import com.vikisol.arena.notifications.entity.Notification;
import com.vikisol.arena.notifications.repository.NotificationRepository;
import com.vikisol.arena.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final com.vikisol.arena.notifications.repository.NotificationPreferenceRepository preferenceRepository;
    private final com.vikisol.arena.auth.repository.UserRepository userRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PagedResponse<NotificationResponse>>> getNotifications(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageLimits.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        // Snoozed ones come back when their time is up (row 16).
        var result = notificationRepository.findVisible(principal.getId(), java.time.Instant.now(), PageLimits.of(page, size));
        return ResponseEntity.ok(ApiResponse.ok(PagedResponse.of(result, this::toResponse)));
    }

    @PutMapping("/{id}/read")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> markRead(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new com.vikisol.arena.common.exception.ResourceNotFoundException("Notification not found"));
        if (!n.getUser().getId().equals(principal.getId())) {
            throw new AccessDeniedException("Not your notification");
        }
        n.setRead(true);
        notificationRepository.save(n);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PutMapping("/read-all")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> markAllRead(@AuthenticationPrincipal UserPrincipal principal) {
        notificationRepository.markAllReadForUser(principal.getId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ARENA-FIX-EVERYTHING.md Phase 1 finding - there was no way to remove a notification at
    // all, so a stale one (e.g. about a post that's since been deleted or cancelled - Notification
    // carries no FK back to the post it's about, so nothing does this automatically) sits forever.
    // Author-only, same shape as markRead above; a hard delete rather than a read/dismissed flag
    // since a notification has no ongoing value once the user is done with it, unlike a post's
    // moderation/room history.
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new com.vikisol.arena.common.exception.ResourceNotFoundException("Notification not found"));
        if (!n.getUser().getId().equals(principal.getId())) {
            throw new AccessDeniedException("Not your notification");
        }
        notificationRepository.delete(n);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // Row 16: hide it for a while ({ minutes } 15-10080, default 60).
    @PostMapping("/{id}/snooze")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> snooze(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
                                                    @RequestBody(required = false) SnoozeRequest request) {
        int minutes = request == null || request.minutes() == null ? 60 : request.minutes();
        if (minutes < 15 || minutes > 10080) throw new com.vikisol.arena.common.exception.BadRequestException("minutes must be between 15 and 10080");
        Notification n = requireOwn(principal.getId(), id);
        n.setSnoozedUntil(java.time.Instant.now().plus(java.time.Duration.ofMinutes(minutes)));
        notificationRepository.save(n);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    public record SnoozeRequest(Integer minutes) {
    }

    // Row 16: the flow's "dismiss" - the same as DELETE /notifications/{id}.
    @PostMapping("/{id}/dismiss")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> dismiss(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        notificationRepository.delete(requireOwn(principal.getId(), id));
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // Row 18: which categories to receive. safety is always on. Row 52 (B+) names them in the
    // plural (messages, activities, needs, jobs) and adds jenny and marketing (opt-in); both
    // spellings are read and written.
    @GetMapping("/preferences")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Preferences>> preferences(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(preferenceRepository.findByUserId(principal.getId())
                .map(p -> Preferences.of(p.isActivity(), p.isNeed(), p.isJob(), p.isMessage(), p.isJenny(), p.isMarketing()))
                .orElse(Preferences.of(true, true, true, true, true, false))));
    }

    @PutMapping("/preferences")
    @Transactional
    public ResponseEntity<ApiResponse<Preferences>> setPreferences(@AuthenticationPrincipal UserPrincipal principal, @RequestBody Preferences request) {
        if (Boolean.FALSE.equals(request.safety())) {
            throw new com.vikisol.arena.common.exception.BadRequestException("Safety notices can't be turned off");
        }
        var p = preferenceRepository.findByUserId(principal.getId()).orElseGet(() -> com.vikisol.arena.notifications.entity.NotificationPreference
                .builder().user(userRepository.getReferenceById(principal.getId())).build());
        Boolean activity = either(request.activity(), request.activities());
        Boolean need = either(request.need(), request.needs());
        Boolean job = either(request.job(), request.jobs());
        Boolean message = either(request.message(), request.messages());
        if (activity != null) p.setActivity(activity);
        if (need != null) p.setNeed(need);
        if (job != null) p.setJob(job);
        if (message != null) p.setMessage(message);
        if (request.jenny() != null) p.setJenny(request.jenny());
        if (request.marketing() != null) p.setMarketing(request.marketing());
        preferenceRepository.save(p);
        return preferences(principal);
    }

    private static Boolean either(Boolean singular, Boolean plural) {
        return singular != null ? singular : plural;
    }

    public record Preferences(Boolean activity, Boolean need, Boolean job, Boolean message, Boolean safety,
                              Boolean activities, Boolean needs, Boolean jobs, Boolean messages, Boolean jenny, Boolean marketing) {
        static Preferences of(boolean activity, boolean need, boolean job, boolean message, boolean jenny, boolean marketing) {
            return new Preferences(activity, need, job, message, true, activity, need, job, message, jenny, marketing);
        }
    }

    private Notification requireOwn(UUID userId, UUID id) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new com.vikisol.arena.common.exception.ResourceNotFoundException("Notification not found"));
        if (!n.getUser().getId().equals(userId)) throw new AccessDeniedException("Not your notification");
        return n;
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId().toString(), n.getType().wireValue(), n.getTitle(), n.getBody(),
                n.getCreatedAt().toString(), n.isRead(), n.getCategory());
    }
}
