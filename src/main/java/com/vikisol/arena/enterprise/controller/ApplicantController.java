package com.vikisol.arena.enterprise.controller;

import com.vikisol.arena.applications.dto.AdvanceStageRequest;
import com.vikisol.arena.applications.entity.ApplicationStage;
import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.dto.PageLimits;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.enterprise.dto.ApplicantResponse;
import com.vikisol.arena.enterprise.service.ApplicantService;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/enterprise")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('RECRUITER','COMPANY_ADMIN')")
public class ApplicantController {

    private final ApplicantService applicantService;
    private final com.vikisol.arena.applications.service.ApplicationService applicationService;

    @GetMapping("/postings/{postingId}/applicants")
    public ResponseEntity<ApiResponse<PagedResponse<ApplicantResponse>>> getApplicants(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID postingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var pageable = PageLimits.of(page, size, Sort.by(Sort.Direction.DESC, "appliedAt"));
        return ResponseEntity.ok(ApiResponse.ok(applicantService.getApplicantsForPosting(principal.getId(), postingId, pageable)));
    }

    @GetMapping("/applicants/{applicantId}")
    public ResponseEntity<ApiResponse<ApplicantResponse>> getApplicant(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID applicantId) {
        return ResponseEntity.ok(ApiResponse.ok(applicantService.getApplicant(principal.getId(), applicantId)));
    }

    @PutMapping("/applicants/{applicantId}/stage")
    public ResponseEntity<ApiResponse<ApplicantResponse>> moveStage(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID applicantId, @Valid @RequestBody AdvanceStageRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                applicantService.moveStage(principal.getId(), applicantId, ApplicationStage.fromWireValue(request.stage()), request.message())));
    }

    // FE-API-GAPS row 30: team-private notes and the application's history.
    @GetMapping("/applicants/{applicantId}/notes")
    public ResponseEntity<ApiResponse<java.util.List<com.vikisol.arena.applications.dto.ApplicationNoteView>>> notes(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID applicantId) {
        return ResponseEntity.ok(ApiResponse.ok(applicationService.notes(principal.getId(), applicantId)));
    }

    @PostMapping("/applicants/{applicantId}/notes")
    public ResponseEntity<ApiResponse<java.util.List<com.vikisol.arena.applications.dto.ApplicationNoteView>>> addNote(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID applicantId, @Valid @RequestBody NoteRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(applicationService.addNote(principal.getId(), applicantId, request.text())));
    }

    public record NoteRequest(@jakarta.validation.constraints.NotBlank(message = "is required")
                              @jakarta.validation.constraints.Size(max = 2000, message = "must be at most 2000 characters") String text) {
    }

    @GetMapping("/applicants/{applicantId}/events")
    public ResponseEntity<ApiResponse<java.util.List<com.vikisol.arena.applications.dto.ApplicationTimeline>>> events(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID applicantId) {
        return ResponseEntity.ok(ApiResponse.ok(applicationService.companyTimeline(principal.getId(), applicantId)));
    }
}
