package com.vikisol.arena.enterprise.controller;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.enterprise.dto.EnterpriseProfileResponse;
import com.vikisol.arena.enterprise.dto.UpdateEnterpriseProfileRequest;
import com.vikisol.arena.enterprise.dto.admin.TeamMemberResponse;
import com.vikisol.arena.enterprise.service.EnterpriseProfileService;
import com.vikisol.arena.enterprise.service.TeamService;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/enterprise/profile")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('RECRUITER','COMPANY_ADMIN')")
public class EnterpriseProfileController {

    private final EnterpriseProfileService enterpriseProfileService;
    private final TeamService teamService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<EnterpriseProfileResponse>> getMyProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(enterpriseProfileService.getMyProfile(principal.getId())));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<EnterpriseProfileResponse>> updateMyProfile(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody UpdateEnterpriseProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(enterpriseProfileService.updateMyProfile(principal.getId(), request)));
    }

    // HM3: a recruiter needs to pick a hiring manager from their own team when scheduling an
    // interview - TeamService.listMembers() itself stays company_admin-only (CA2's full team
    // management surface), this is a narrower read any workspace member can use.
    @GetMapping("/hiring-managers")
    public ResponseEntity<ApiResponse<List<TeamMemberResponse>>> hiringManagers(@AuthenticationPrincipal UserPrincipal principal) {
        List<TeamMemberResponse> hiringManagers = teamService.listMembers(principal.getId()).stream()
                .filter(m -> "hiring_manager".equals(m.role()) && "active".equals(m.status()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(hiringManagers));
    }
}
