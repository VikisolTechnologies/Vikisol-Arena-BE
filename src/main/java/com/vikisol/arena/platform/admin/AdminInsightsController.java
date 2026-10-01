package com.vikisol.arena.platform.admin;

import com.vikisol.arena.audit.AuditCsv;
import com.vikisol.arena.audit.AuditEventResponse;
import com.vikisol.arena.audit.AuditService;
import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.dto.PageLimits;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// FE-API-GAPS rows 42, 44, 45, 47, 48, 49 (admin, B+). Under /admin: platform-admin role and
// the admin 2FA filter apply. The one write here (takedown, launch areas) is audited.
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminInsightsController {

    private final AdminInsightsService insights;
    private final AuditService auditService;

    public record ReasonRequest(@NotBlank(message = "is required") @Size(max = 500, message = "must be at most 500 characters") String reason) {
    }

    public record AreasRequest(@NotNull(message = "is required") List<String> areas) {
    }

    // Row 42.
    @GetMapping("/metrics/launch")
    public ResponseEntity<ApiResponse<AdminInsightsService.LaunchMetrics>> launch(@RequestParam(required = false) Integer sinceDays) {
        return ResponseEntity.ok(ApiResponse.ok(insights.launchMetrics(sinceDays == null ? null : Math.max(1, sinceDays))));
    }

    // Row 44.
    @GetMapping("/content")
    public ResponseEntity<ApiResponse<List<AdminInsightsService.ContentItem>>> content(
            @RequestParam(required = false) String kind, @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PageLimits.ok(insights.content(kind, query, PageLimits.of(page, size)));
    }

    @PutMapping("/content/{id}/takedown")
    public ResponseEntity<ApiResponse<AdminInsightsService.ContentItem>> takedown(
            @AuthenticationPrincipal UserPrincipal admin, @PathVariable UUID id, @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(insights.takedown(admin.getId(), id, request.reason())));
    }

    // Row 45.
    @GetMapping("/catalog/activity-types")
    public ResponseEntity<ApiResponse<List<AdminInsightsService.ActivityType>>> activityTypes() {
        return ResponseEntity.ok(ApiResponse.ok(insights.activityTypes()));
    }

    // Row 47: what Arena knows. Automations and cover flags are JennySol's (GAP-MAPPING).
    @GetMapping("/jenny/actions")
    public ResponseEntity<ApiResponse<List<AuditEventResponse>>> jennyActions(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PageLimits.ok(auditService.agentActions(PageLimits.of(page, size)));
    }

    @GetMapping("/jenny/providers")
    public ResponseEntity<ApiResponse<List<AdminInsightsService.ProviderStatus>>> providers() {
        return ResponseEntity.ok(ApiResponse.ok(insights.providers()));
    }

    // Row 48.
    @GetMapping("/audit")
    public ResponseEntity<ApiResponse<List<AuditEventResponse>>> audit(
            @RequestParam(required = false) Integer sinceDays, @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PageLimits.ok(auditService.searchPlatform(actorId, action, since(sinceDays), PageLimits.of(page, size)));
    }

    @GetMapping(value = "/audit/export", produces = "text/csv")
    public ResponseEntity<String> exportAudit(
            @RequestParam(required = false) Integer sinceDays, @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID actorId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"arena-audit-log.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(AuditCsv.write(auditService.exportPlatform(actorId, action, since(sinceDays))));
    }

    // Row 49.
    @GetMapping("/team")
    public ResponseEntity<ApiResponse<List<AdminInsightsService.StaffMember>>> team() {
        return ResponseEntity.ok(ApiResponse.ok(insights.team()));
    }

    @PutMapping("/team/{id}/launch-areas")
    public ResponseEntity<ApiResponse<AdminInsightsService.StaffMember>> launchAreas(
            @AuthenticationPrincipal UserPrincipal admin, @PathVariable UUID id, @Valid @RequestBody AreasRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(insights.setLaunchAreas(admin.getId(), id, request.areas())));
    }

    private static Instant since(Integer sinceDays) {
        return sinceDays == null ? null : Instant.now().minus(Duration.ofDays(Math.max(1, sinceDays)));
    }
}
