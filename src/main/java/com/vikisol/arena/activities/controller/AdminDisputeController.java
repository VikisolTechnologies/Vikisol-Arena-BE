package com.vikisol.arena.activities.controller;

import com.vikisol.arena.activities.service.AdminDisputeService;
import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.dto.PageLimits;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Flow §9 disputes queue. Under /admin: platform-admin role and the admin 2FA filter apply.
@RestController
@RequestMapping("/admin/disputes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminDisputeController {

    private final AdminDisputeService disputeService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminDisputeService.DisputeView>>> queue(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageLimits.ok(disputeService.queue(status, PageLimits.of(page, size)));
    }

    @PutMapping("/{attendanceId}/accept")
    public ResponseEntity<ApiResponse<AdminDisputeService.DisputeView>> accept(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID attendanceId, @Valid @RequestBody(required = false) NoteRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(disputeService.decide(principal.getId(), attendanceId, true, request == null ? null : request.note())));
    }

    @PutMapping("/{attendanceId}/reject")
    public ResponseEntity<ApiResponse<AdminDisputeService.DisputeView>> reject(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID attendanceId, @Valid @RequestBody NoteRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(disputeService.decide(principal.getId(), attendanceId, false, request.note())));
    }

    public record NoteRequest(@Size(max = 500, message = "must be at most 500 characters") String note) {
    }
}
