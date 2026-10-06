package com.vikisol.arena.business.controller;

import com.vikisol.arena.business.dto.BusinessDtos.*;
import com.vikisol.arena.business.service.BusinessVerificationService;
import com.vikisol.arena.business.service.TeamRoles;
import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// G27-G28 (API-CHANGES.md).
@RestController
@RequiredArgsConstructor
public class BusinessController {

    private final BusinessVerificationService verificationService;

    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @PostMapping("/enterprise/verification")
    public ResponseEntity<ApiResponse<VerificationView>> submit(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody SubmitRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("We sent a code to that work email", verificationService.submit(principal.getId(), request)));
    }

    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    @PostMapping("/enterprise/verification/confirm")
    public ResponseEntity<ApiResponse<VerificationView>> confirm(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody ConfirmRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(verificationService.confirm(principal.getId(), request.code())));
    }

    @PreAuthorize("hasAnyRole('RECRUITER','COMPANY_ADMIN','HIRING_MANAGER')")
    @GetMapping("/enterprise/verification")
    public ResponseEntity<ApiResponse<VerificationView>> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(verificationService.mine(principal.getId())));
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/companies/{id}/verification")
    public ResponseEntity<ApiResponse<PublicBadge>> badge(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(verificationService.badge(id)));
    }

    @PreAuthorize("hasAnyRole('RECRUITER','COMPANY_ADMIN','HIRING_MANAGER')")
    @GetMapping("/enterprise/team/roles")
    public ResponseEntity<ApiResponse<List<TeamRoles.RoleView>>> roles() {
        return ResponseEntity.ok(ApiResponse.ok(TeamRoles.CATALOGUE));
    }
}
