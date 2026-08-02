package com.vikisol.arena.enterprise.controller;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.enterprise.dto.EnterpriseProfileResponse;
import com.vikisol.arena.enterprise.dto.UpdateEnterpriseProfileRequest;
import com.vikisol.arena.enterprise.service.EnterpriseProfileService;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/enterprise/profile")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ENTERPRISE')")
public class EnterpriseProfileController {

    private final EnterpriseProfileService enterpriseProfileService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<EnterpriseProfileResponse>> getMyProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(enterpriseProfileService.getMyProfile(principal.getId())));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<EnterpriseProfileResponse>> updateMyProfile(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody UpdateEnterpriseProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(enterpriseProfileService.updateMyProfile(principal.getId(), request)));
    }
}
