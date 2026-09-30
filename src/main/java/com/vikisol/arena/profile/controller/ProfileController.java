package com.vikisol.arena.profile.controller;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.profile.dto.CandidateDataExport;
import com.vikisol.arena.profile.dto.CandidateProfileResponse;
import com.vikisol.arena.profile.dto.ConsentDto;
import com.vikisol.arena.profile.dto.LocationConsentRequest;
import com.vikisol.arena.profile.dto.PatchProfileRequest;
import com.vikisol.arena.profile.dto.ProfileBasicsResponse;
import com.vikisol.arena.profile.dto.ProfileListRequest;
import com.vikisol.arena.profile.dto.PublicCandidateProfileResponse;
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

import java.util.UUID;

@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TALENT')")
public class ProfileController {

    private final CandidateProfileService profileService;
    private final com.vikisol.arena.platform.service.ModerationService moderationService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CandidateProfileResponse>> getMyProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(profileService.getMyProfile(principal.getId())));
    }

    // Phase C profile revamp - the public/other-user view. {id} is a user id (matches how
    // Follow/Post already key on user ids everywhere else in this API); a CandidateProfile id
    // (what Talent Universe search returns) is also accepted and resolves to the same profile.
    // ARENA-INVENTORY-FIXES.md FIX 1 - overrides the class-level hasRole('TALENT') so a
    // logged-out visitor (or a non-talent role, e.g. a recruiter) can view it; principal is
    // therefore nullable here and profileService.getPublicProfile already treats a null
    // viewingUserId as "anonymous" (no viewerFollows, no self-check).
    @PreAuthorize("permitAll()")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PublicCandidateProfileResponse>> getPublicProfile(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        UUID viewerId = principal == null ? null : principal.getId();
        return ResponseEntity.ok(ApiResponse.ok(profileService.getPublicProfile(id, viewerId)));
    }

    // FE-API-GAPS row 61: report a person. `id` is their user id or profile id, as for GET above.
    // Evidence files work as for post reports (POST /reports/evidence first).
    @PostMapping("/{id}/report")
    public ResponseEntity<ApiResponse<Void>> reportPerson(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
                                                          @Valid @RequestBody com.vikisol.arena.posts.dto.ReportPostRequest request) {
        moderationService.fileUserReport(principal.getId(), profileService.resolveUserId(id), request.reason(), request.evidenceUrls());
        return ResponseEntity.ok(ApiResponse.ok("Report submitted", null));
    }

    // --- FE-API-GAPS 1-5: onboarding basics (see API-CHANGES.md) ---

    @GetMapping("/me/basics")
    public ResponseEntity<ApiResponse<ProfileBasicsResponse>> getBasics(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(profileService.getBasics(principal.getId())));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<ProfileBasicsResponse>> patch(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody PatchProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(profileService.patch(principal.getId(), request)));
    }

    @PutMapping("/me/intents")
    public ResponseEntity<ApiResponse<ProfileBasicsResponse>> setIntents(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody ProfileListRequest.Intents request) {
        return ResponseEntity.ok(ApiResponse.ok(profileService.setIntents(principal.getId(), request.intents())));
    }

    // Row 18: who can find you in people search - nearby | everyone | hidden.
    @GetMapping("/me/visibility")
    public ResponseEntity<ApiResponse<VisibilityBody>> visibility(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(new VisibilityBody(profileService.visibility(principal.getId()))));
    }

    @PutMapping("/me/visibility")
    public ResponseEntity<ApiResponse<VisibilityBody>> setVisibility(@AuthenticationPrincipal UserPrincipal principal, @RequestBody VisibilityBody request) {
        return ResponseEntity.ok(ApiResponse.ok(new VisibilityBody(profileService.setVisibility(principal.getId(), request.profile()))));
    }

    public record VisibilityBody(String profile) {
    }

    @PutMapping("/me/interests")
    public ResponseEntity<ApiResponse<ProfileBasicsResponse>> setInterests(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody ProfileListRequest.Interests request) {
        return ResponseEntity.ok(ApiResponse.ok(profileService.setInterests(principal.getId(), request.interests())));
    }

    @PostMapping(value = "/me/photo", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<ProfileBasicsResponse>> uploadPhoto(
            @AuthenticationPrincipal UserPrincipal principal, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok(profileService.uploadPhoto(principal.getId(), file)));
    }

    @DeleteMapping("/me/photo")
    public ResponseEntity<ApiResponse<ProfileBasicsResponse>> deletePhoto(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(profileService.deletePhoto(principal.getId())));
    }

    @PutMapping("/me/details")
    public ResponseEntity<ApiResponse<CandidateProfileResponse>> updateDetails(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody UpdateProfileDetailsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(profileService.updateDetails(
                principal.getId(), request.name(), request.title(), request.industry(),
                request.experienceYears(), request.rateFloor(), request.openTo(),
                request.cameForJob(), request.organization(), request.currentCtc(),
                request.expectedCtc(), request.preferredLocation())));
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
