package com.vikisol.arena.applications.controller;

import com.vikisol.arena.applications.dto.AdvanceStageRequest;
import com.vikisol.arena.applications.dto.ApplicationResponse;
import com.vikisol.arena.applications.dto.ApplyRequest;
import com.vikisol.arena.applications.entity.ApplicationStage;
import com.vikisol.arena.applications.service.ApplicationService;
import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.dto.PagedResponse;
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
@RequestMapping("/applications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TALENT')")
public class ApplicationController {

    private final ApplicationService applicationService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ApplicationResponse>>> getMyApplications(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "appliedAt"));
        return ResponseEntity.ok(ApiResponse.ok(applicationService.getMyApplications(principal.getId(), pageable)));
    }

    @GetMapping("/exists")
    public ResponseEntity<ApiResponse<Boolean>> hasApplied(@AuthenticationPrincipal UserPrincipal principal, @RequestParam UUID jobId) {
        return ResponseEntity.ok(ApiResponse.ok(applicationService.hasAppliedTo(principal.getId(), jobId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ApplicationResponse>> apply(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody ApplyRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Application submitted",
                applicationService.applyToJob(principal.getId(), UUID.fromString(request.jobId()))));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> withdraw(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        applicationService.withdraw(principal.getId(), id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PutMapping("/{id}/stage")
    public ResponseEntity<ApiResponse<ApplicationResponse>> advanceStage(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody AdvanceStageRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                applicationService.advanceStageAsCandidate(principal.getId(), id, ApplicationStage.fromWireValue(request.stage()))));
    }
}
