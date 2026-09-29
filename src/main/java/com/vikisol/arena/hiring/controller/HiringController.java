package com.vikisol.arena.hiring.controller;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.dto.PageLimits;
import com.vikisol.arena.hiring.dto.HiringDtos.*;
import com.vikisol.arena.hiring.service.HiringService;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// G22-G26 (API-CHANGES.md). Three audiences, three role guards, one controller:
// employers (recruiter / company admin, tenant-checked in the service), candidates (their own
// application only), and anyone signed in (what a job asks for).
@RestController
@RequiredArgsConstructor
public class HiringController {

    private static final String EMPLOYER = "hasAnyRole('RECRUITER','COMPANY_ADMIN')";

    private final HiringService hiringService;

    // --- employer ---

    @PreAuthorize(EMPLOYER)
    @PutMapping("/enterprise/postings/{id}/requirements")
    public ResponseEntity<ApiResponse<JobRequirementsView>> setRequirements(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody RequirementsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(hiringService.setRequirements(principal.getId(), id, request)));
    }

    @PreAuthorize(EMPLOYER)
    @PutMapping("/enterprise/postings/{id}/screening")
    public ResponseEntity<ApiResponse<JobRequirementsView>> setScreening(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody ScreeningRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(hiringService.setScreening(principal.getId(), id, request.questions())));
    }

    @PreAuthorize(EMPLOYER)
    @GetMapping("/enterprise/postings/{id}/funnel")
    public ResponseEntity<ApiResponse<FunnelView>> funnel(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(hiringService.funnel(principal.getId(), id)));
    }

    @PreAuthorize(EMPLOYER)
    @GetMapping("/enterprise/postings/{id}/evidence")
    public ResponseEntity<ApiResponse<List<ApplicantEvidenceRow>>> evidenceSummaries(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size) {
        return PageLimits.ok(hiringService.evidenceSummaries(principal.getId(), id, PageLimits.of(page, size)));
    }

    @PreAuthorize(EMPLOYER)
    @GetMapping("/enterprise/applicants/{id}/evidence")
    public ResponseEntity<ApiResponse<ApplicantEvidenceView>> applicantEvidence(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(hiringService.applicantEvidence(principal.getId(), id)));
    }

    @PreAuthorize(EMPLOYER)
    @PutMapping("/enterprise/applicants/{id}/requirements/{requirementId}")
    public ResponseEntity<ApiResponse<ApplicantEvidenceView>> assess(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @PathVariable UUID requirementId,
            @Valid @RequestBody AssessmentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(hiringService.assess(principal.getId(), id, requirementId, request)));
    }

    // --- candidate ---

    @PreAuthorize("hasRole('TALENT')")
    @GetMapping("/applications/{id}/screening")
    public ResponseEntity<ApiResponse<CandidateScreeningView>> myScreening(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(hiringService.candidateScreening(principal.getId(), id)));
    }

    @PreAuthorize("hasRole('TALENT')")
    @PutMapping("/applications/{id}/screening")
    public ResponseEntity<ApiResponse<CandidateScreeningView>> saveScreening(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody ScreeningAnswersRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(hiringService.saveScreening(principal.getId(), id, request)));
    }

    // --- anyone signed in ---

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/jobs/{id}/requirements")
    public ResponseEntity<ApiResponse<JobRequirementsView>> requirements(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(hiringService.requirements(id)));
    }
}
