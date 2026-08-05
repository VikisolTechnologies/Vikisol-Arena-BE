package com.vikisol.arena.enterprise.controller;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.enterprise.dto.CreatePostingRequest;
import com.vikisol.arena.enterprise.dto.JobPostingResponse;
import com.vikisol.arena.enterprise.dto.SetPostingStatusRequest;
import com.vikisol.arena.enterprise.service.JobPostingService;
import com.vikisol.arena.jobs.entity.PostingStatus;
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
@RequestMapping("/enterprise/postings")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('RECRUITER','COMPANY_ADMIN')")
public class JobPostingController {

    private final JobPostingService jobPostingService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<JobPostingResponse>>> getMyPostings(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.ok(jobPostingService.getMyPostings(principal.getId(), pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<JobPostingResponse>> getPosting(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(jobPostingService.getPosting(principal.getId(), id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<JobPostingResponse>> createPosting(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody CreatePostingRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Posting created", jobPostingService.createPosting(principal.getId(), request)));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Void>> setStatus(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody SetPostingStatusRequest request) {
        jobPostingService.setStatus(principal.getId(), id, PostingStatus.fromWireValue(request.status()));
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
