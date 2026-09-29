package com.vikisol.arena.business.controller;

import com.vikisol.arena.business.dto.BusinessDtos.QueueItem;
import com.vikisol.arena.business.dto.BusinessDtos.RejectRequest;
import com.vikisol.arena.business.service.BusinessVerificationService;
import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.dto.PageLimits;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// ARENA-APP-FLOW §9 verification queue (FE-API-GAPS row 29). Under /admin, so the platform-admin
// role and the admin 2FA filter (PlatformAdminMfaFilter) apply exactly as for the other admin pages.
@RestController
@RequestMapping("/admin/verifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminVerificationController {

    private final BusinessVerificationService verificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<QueueItem>>> queue(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageLimits.ok(verificationService.queue(status, PageLimits.of(page, size)));
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<QueueItem>> approve(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(verificationService.approve(principal.getId(), id)));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<QueueItem>> reject(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody RejectRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(verificationService.reject(principal.getId(), id, request.note())));
    }
}
