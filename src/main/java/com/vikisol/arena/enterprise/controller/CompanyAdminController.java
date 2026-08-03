package com.vikisol.arena.enterprise.controller;

import com.vikisol.arena.audit.AuditEventResponse;
import com.vikisol.arena.audit.AuditService;
import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.enterprise.dto.admin.*;
import com.vikisol.arena.enterprise.service.AdminDashboardService;
import com.vikisol.arena.enterprise.service.BillingService;
import com.vikisol.arena.enterprise.service.EnterpriseProfileService;
import com.vikisol.arena.enterprise.service.TalentSearchService;
import com.vikisol.arena.enterprise.service.TeamService;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/** CA1-CA7 (company_admin only). CA5 (company profile) and C7 (enter the recruiter workspace)
 * need no new endpoints - EnterpriseProfileController already serves profile reads/writes to
 * both roles, and every existing /enterprise/** route already accepts COMPANY_ADMIN. */
@RestController
@RequestMapping("/enterprise/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COMPANY_ADMIN')")
public class CompanyAdminController {

    private final AdminDashboardService dashboardService;
    private final TeamService teamService;
    private final AuditService auditService;
    private final BillingService billingService;
    private final TalentSearchService talentSearchService;
    private final EnterpriseProfileService enterpriseProfileService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> dashboard(
            @AuthenticationPrincipal UserPrincipal principal, @RequestParam(defaultValue = "7") int range) {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.getDashboard(principal.getId(), range)));
    }

    @GetMapping("/team")
    public ResponseEntity<ApiResponse<List<TeamMemberResponse>>> team(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(teamService.listMembers(principal.getId())));
    }

    @GetMapping("/team/invitations")
    public ResponseEntity<ApiResponse<List<InvitationResponse>>> invitations(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(teamService.listPendingInvitations(principal.getId())));
    }

    @PostMapping("/team/invite")
    public ResponseEntity<ApiResponse<InvitationResponse>> invite(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody InviteMemberRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Invitation sent", teamService.invite(principal.getId(), request)));
    }

    @DeleteMapping("/team/invitations/{id}")
    public ResponseEntity<ApiResponse<Void>> revokeInvitation(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        teamService.revokeInvitation(principal.getId(), id);
        return ResponseEntity.ok(ApiResponse.ok("Invitation revoked", null));
    }

    @PutMapping("/team/{membershipId}/role")
    public ResponseEntity<ApiResponse<Void>> changeRole(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID membershipId, @Valid @RequestBody ChangeRoleRequest request) {
        teamService.changeRole(principal.getId(), membershipId, request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PutMapping("/team/{membershipId}/suspend")
    public ResponseEntity<ApiResponse<Void>> suspend(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID membershipId) {
        teamService.setSuspended(principal.getId(), membershipId, true);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PutMapping("/team/{membershipId}/reactivate")
    public ResponseEntity<ApiResponse<Void>> reactivate(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID membershipId) {
        teamService.setSuspended(principal.getId(), membershipId, false);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/team/{membershipId}")
    public ResponseEntity<ApiResponse<Void>> remove(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID membershipId) {
        teamService.remove(principal.getId(), membershipId);
        return ResponseEntity.ok(ApiResponse.ok("Member removed", null));
    }

    @GetMapping("/audit")
    public ResponseEntity<ApiResponse<PagedResponse<AuditEventResponse>>> audit(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID actorId, @RequestParam(required = false) String action,
            @RequestParam(required = false) Integer sinceDays,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Instant since = sinceDays == null ? null : Instant.now().minus(sinceDays, ChronoUnit.DAYS);
        return ResponseEntity.ok(ApiResponse.ok(
                auditService.search(tenantIdFor(principal), actorId, action, since, PageRequest.of(page, size))));
    }

    @GetMapping(value = "/audit/export", produces = "text/csv")
    public ResponseEntity<String> exportAudit(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID actorId, @RequestParam(required = false) String action,
            @RequestParam(required = false) Integer sinceDays) {
        Instant since = sinceDays == null ? null : Instant.now().minus(sinceDays, ChronoUnit.DAYS);
        List<AuditEventResponse> rows = auditService.exportAll(tenantIdFor(principal), actorId, action, since);
        StringBuilder csv = new StringBuilder("Time,Actor,Action,Target,Metadata\n");
        for (AuditEventResponse r : rows) {
            csv.append(csvEscape(r.createdAt())).append(',')
                    .append(csvEscape(r.actorName())).append(',')
                    .append(csvEscape(r.action())).append(',')
                    .append(csvEscape(r.target())).append(',')
                    .append(csvEscape(r.metadata())).append('\n');
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-log.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.toString());
    }

    @GetMapping("/billing")
    public ResponseEntity<ApiResponse<BillingResponse>> billing(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(billingService.getBilling(principal.getId())));
    }

    @PutMapping("/billing/plan")
    public ResponseEntity<ApiResponse<BillingResponse>> changePlan(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody ChangePlanRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(billingService.changePlan(principal.getId(), request)));
    }

    @GetMapping("/consent")
    public ResponseEntity<ApiResponse<List<ConsentEntryResponse>>> consent(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(talentSearchService.getConsentView(principal.getId())));
    }

    private UUID tenantIdFor(UserPrincipal principal) {
        return enterpriseProfileService.getEntityForUser(principal.getId()).getId();
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
