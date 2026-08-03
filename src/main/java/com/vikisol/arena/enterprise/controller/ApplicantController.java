package com.vikisol.arena.enterprise.controller;

import com.vikisol.arena.applications.dto.AdvanceStageRequest;
import com.vikisol.arena.applications.entity.ApplicationStage;
import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.enterprise.dto.ApplicantResponse;
import com.vikisol.arena.enterprise.service.ApplicantService;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
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

    @GetMapping("/postings/{postingId}/applicants")
    public ResponseEntity<ApiResponse<PagedResponse<ApplicantResponse>>> getApplicants(
            @PathVariable UUID postingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "appliedAt"));
        return ResponseEntity.ok(ApiResponse.ok(applicantService.getApplicantsForPosting(postingId, pageable)));
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
                applicantService.moveStage(principal.getId(), applicantId, ApplicationStage.fromWireValue(request.stage()))));
    }
}
