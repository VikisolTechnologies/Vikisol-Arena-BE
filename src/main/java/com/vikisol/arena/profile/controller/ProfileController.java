package com.vikisol.arena.profile.controller;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.profile.dto.CandidateDataExport;
import com.vikisol.arena.profile.dto.CandidateProfileResponse;
import com.vikisol.arena.profile.dto.ConsentDto;
import com.vikisol.arena.profile.dto.LocationConsentRequest;
import com.vikisol.arena.profile.dto.UpdateAutonomyRequest;
import com.vikisol.arena.profile.dto.UpdateProfileDetailsRequest;
import com.vikisol.arena.profile.dto.UpdateSkillsRequest;
import com.vikisol.arena.profile.entity.AutonomyLevel;
import com.vikisol.arena.profile.service.CandidateProfileService;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
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

    @PutMapping("/me/details")
    public ResponseEntity<ApiResponse<CandidateProfileResponse>> updateDetails(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody UpdateProfileDetailsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(profileService.updateDetails(
                principal.getId(), request.name(), request.title(), request.industry(),
                request.experienceYears(), request.rateFloor(), request.openTo())));
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

    @PutMapping("/me/location")
    public ResponseEntity<ApiResponse<CandidateProfileResponse>> updateLocationConsent(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody LocationConsentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(profileService.updateLocationConsent(principal.getId(), request)));
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

    // DPDP self-service data rights (ARENA-SHIP-IT.md #5).
    @GetMapping("/me/export")
    public ResponseEntity<ApiResponse<CandidateDataExport>> exportMyData(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(profileService.exportMyData(principal.getId())));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteMyAccount(
            @AuthenticationPrincipal UserPrincipal principal, HttpServletRequest request) {
        profileService.deleteMyAccount(principal.getId(), extractBearerToken(request));
        return ResponseEntity.ok(ApiResponse.ok("Your account and personal data have been erased", null));
    }

    private String extractBearerToken(HttpServletRequest request) {
        String bearer = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
