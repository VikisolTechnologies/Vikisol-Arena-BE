package com.vikisol.arena.profile.controller;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.profile.dto.CandidateProfileResponse;
import com.vikisol.arena.profile.dto.ConsentDto;
import com.vikisol.arena.profile.dto.UpdateAutonomyRequest;
import com.vikisol.arena.profile.dto.UpdateSkillsRequest;
import com.vikisol.arena.profile.entity.AutonomyLevel;
import com.vikisol.arena.profile.service.CandidateProfileService;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TALENT')")
public class ProfileController {

    private final CandidateProfileService profileService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CandidateProfileResponse>> getMyProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(profileService.getMyProfile(principal.getId())));
    }

    @PutMapping("/me/skills")
    public ResponseEntity<ApiResponse<CandidateProfileResponse>> updateSkills(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody UpdateSkillsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(profileService.updateSkills(principal.getId(), request.skills())));
    }

    @PutMapping("/me/consent")
    public ResponseEntity<ApiResponse<CandidateProfileResponse>> updateConsent(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody ConsentDto request) {
        return ResponseEntity.ok(ApiResponse.ok(profileService.updateConsent(principal.getId(), request)));
    }

    @PostMapping(value = "/me/cv", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<CandidateProfileResponse>> uploadCv(
            @AuthenticationPrincipal UserPrincipal principal, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok("CV uploaded", profileService.uploadCv(principal.getId(), file)));
    }

    @PutMapping("/me/autonomy")
    public ResponseEntity<ApiResponse<CandidateProfileResponse>> updateAutonomy(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody UpdateAutonomyRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                profileService.updateAutonomy(principal.getId(), AutonomyLevel.fromWireValue(request.autonomy()))));
    }
}
