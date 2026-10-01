package com.vikisol.arena.platform.admin;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// FE-API-GAPS rows 50-51 (admin, B+). Under /admin: platform-admin role and the admin 2FA filter
// (PlatformAdminMfaFilter) apply. Every action is audited with its reason (AdminAccountService).
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminAccountController {

    private final AdminAccountService accounts;

    public record ReasonRequest(@NotBlank(message = "is required") @Size(max = 500, message = "must be at most 500 characters") String reason) {
    }

    public record SuspendRequest(@NotBlank(message = "is required") @Size(max = 500, message = "must be at most 500 characters") String reason,
                                 @Min(value = 1, message = "must be at least 1") @Max(value = 365, message = "must be at most 365") Integer durationDays) {
    }

    public record OptionalReason(@Size(max = 500, message = "must be at most 500 characters") String reason) {
    }

    // --- row 51: accounts ---

    @GetMapping("/users/{id}")
    public ResponseEntity<ApiResponse<AdminAccountService.AccountDetail>> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(accounts.detail(id)));
    }

    @PutMapping("/users/{id}/suspend")
    public ResponseEntity<ApiResponse<AdminAccountService.AccountDetail>> suspend(
            @AuthenticationPrincipal UserPrincipal admin, @PathVariable UUID id, @Valid @RequestBody SuspendRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(accounts.suspend(admin.getId(), id, request.reason(), request.durationDays())));
    }

    @PutMapping("/users/{id}/restore")
    public ResponseEntity<ApiResponse<AdminAccountService.AccountDetail>> restore(
            @AuthenticationPrincipal UserPrincipal admin, @PathVariable UUID id, @Valid @RequestBody(required = false) OptionalReason request) {
        return ResponseEntity.ok(ApiResponse.ok(accounts.restore(admin.getId(), id, request == null ? null : request.reason())));
    }

    @PostMapping("/users/{id}/force-signout")
    public ResponseEntity<ApiResponse<Void>> forceSignOut(
            @AuthenticationPrincipal UserPrincipal admin, @PathVariable UUID id, @Valid @RequestBody(required = false) OptionalReason request) {
        accounts.forceSignOut(admin.getId(), id, request == null ? null : request.reason());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // --- row 50: acting on the account behind a report ---

    @PutMapping("/moderation/{id}/warn")
    public ResponseEntity<ApiResponse<Void>> warn(
            @AuthenticationPrincipal UserPrincipal admin, @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        accounts.warn(admin.getId(), accounts.reportedUser(id), request.reason());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PutMapping("/moderation/{id}/suspend")
    public ResponseEntity<ApiResponse<AdminAccountService.AccountDetail>> suspendReported(
            @AuthenticationPrincipal UserPrincipal admin, @PathVariable UUID id, @Valid @RequestBody SuspendRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(accounts.suspend(admin.getId(), accounts.reportedUser(id), request.reason(), request.durationDays())));
    }

    @PutMapping("/moderation/{id}/ban")
    public ResponseEntity<ApiResponse<AdminAccountService.AccountDetail>> ban(
            @AuthenticationPrincipal UserPrincipal admin, @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(accounts.ban(admin.getId(), accounts.reportedUser(id), request.reason())));
    }
}
