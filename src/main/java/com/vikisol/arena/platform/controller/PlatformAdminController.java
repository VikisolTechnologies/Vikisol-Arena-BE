package com.vikisol.arena.platform.controller;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.platform.dto.*;
import com.vikisol.arena.platform.service.FeatureFlagService;
import com.vikisol.arena.platform.service.ModerationService;
import com.vikisol.arena.platform.service.PlatformAnalyticsService;
import com.vikisol.arena.platform.service.PlatformDashboardService;
import com.vikisol.arena.platform.service.PlatformTenantService;
import com.vikisol.arena.platform.service.PlatformUserService;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * PA1-PA6 (platform_admin only - PA7's own requirement, see class-level @PreAuthorize).
 * A failed check here 403s at the API level (GlobalExceptionHandler), which is fine for a JSON
 * API; PA7's "404 not 403" instruction is about not revealing this console's existence to a
 * human browsing arena-web, which is handled client-side (see PlatformAdminShell rendering a
 * real not-found page for any non-platform_admin session, not just redirecting elsewhere).
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformAdminController {

    private final PlatformDashboardService dashboardService;
    private final PlatformTenantService tenantService;
    private final PlatformUserService userService;
    private final ModerationService moderationService;
    private final PlatformAnalyticsService analyticsService;
    private final FeatureFlagService featureFlagService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<PlatformDashboardResponse>> dashboard() {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getDashboard()));
    }

    @GetMapping("/tenants")
    public ResponseEntity<ApiResponse<PagedResponse<TenantSummaryResponse>>> tenants(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(tenantService.listTenants(query, PageRequest.of(page, size))));
    }

    @PutMapping("/tenants/{id}/suspend")
    public ResponseEntity<ApiResponse<Void>> suspendTenant(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        tenantService.setSuspended(principal.getId(), id, true);
        return ResponseEntity.ok(ApiResponse.ok("Tenant suspended", null));
    }

    @PutMapping("/tenants/{id}/reactivate")
    public ResponseEntity<ApiResponse<Void>> reactivateTenant(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        tenantService.setSuspended(principal.getId(), id, false);
        return ResponseEntity.ok(ApiResponse.ok("Tenant reactivated", null));
    }

    @PutMapping("/tenants/{id}/subscription")
    public ResponseEntity<ApiResponse<TenantSummaryResponse>> adjustSubscription(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody AdjustSubscriptionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(tenantService.adjustSubscription(principal.getId(), id, request)));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PagedResponse<PlatformUserResponse>>> users(
            @RequestParam(required = false) String query, @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(userService.search(query, role, PageRequest.of(page, size))));
    }

    // See PlatformUserService.eraseAccount's own comment for why this reuses the existing
    // DPDP right-to-erasure path rather than a new hard-delete mechanism.
    @DeleteMapping("/users/{id}")
    public ResponseEntity<ApiResponse<Void>> eraseUser(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        userService.eraseAccount(principal.getId(), id);
        return ResponseEntity.ok(ApiResponse.ok("Account erased", null));
    }

    @GetMapping("/moderation")
    public ResponseEntity<ApiResponse<PagedResponse<ModerationItemResponse>>> moderationQueue(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(moderationService.listQueue(status, PageRequest.of(page, size))));
    }

    @PutMapping("/moderation/{id}/dismiss")
    public ResponseEntity<ApiResponse<Void>> dismissModeration(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        moderationService.dismiss(principal.getId(), id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PutMapping("/moderation/{id}/takedown")
    public ResponseEntity<ApiResponse<Void>> takedownModeration(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        moderationService.takedown(principal.getId(), id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/analytics")
    public ResponseEntity<ApiResponse<PlatformAnalyticsResponse>> analytics() {
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.getAnalytics()));
    }

    @GetMapping("/flags")
    public ResponseEntity<ApiResponse<List<FeatureFlagResponse>>> flags() {
        return ResponseEntity.ok(ApiResponse.ok(featureFlagService.list()));
    }

    @PostMapping("/flags")
    public ResponseEntity<ApiResponse<FeatureFlagResponse>> createFlag(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody UpsertFeatureFlagRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(featureFlagService.create(principal.getId(), request)));
    }

    @PutMapping("/flags/{id}")
    public ResponseEntity<ApiResponse<FeatureFlagResponse>> toggleFlag(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody ToggleFeatureFlagRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(featureFlagService.setEnabled(principal.getId(), id, request.enabled())));
    }
}
