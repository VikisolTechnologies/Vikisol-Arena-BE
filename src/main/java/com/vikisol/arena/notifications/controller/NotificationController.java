package com.vikisol.arena.notifications.controller;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.notifications.dto.NotificationResponse;
import com.vikisol.arena.notifications.entity.Notification;
import com.vikisol.arena.notifications.repository.NotificationRepository;
import com.vikisol.arena.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PagedResponse<NotificationResponse>>> getNotifications(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = notificationRepository.findByUserIdOrderByCreatedAtDesc(principal.getId(), pageable);
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

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId().toString(), n.getType().wireValue(), n.getTitle(), n.getBody(),
                n.getCreatedAt().toString(), n.isRead());
    }
}
