package com.vikisol.arena.jobs.controller;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.jobs.dto.JobResponse;
import com.vikisol.arena.jobs.service.JobService;
import com.vikisol.arena.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    // ARENA-INVENTORY-FIXES.md FIX 1 - /jobs (GET, list only) is permitAll'd in SecurityConfig
    // so Discover works logged-out; principal is therefore nullable here. jobService.getOpenJobs
    // already treats a null viewingUserId as "anonymous" (no personalized match scoring).
    // /jobs/{id} detail stays authenticated - not in FIX 1's scope, left untouched.
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<JobResponse>>> getJobs(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        UUID viewerId = principal == null ? null : principal.getId();
        return ResponseEntity.ok(ApiResponse.ok(jobService.getOpenJobs(pageable, viewerId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<JobResponse>> getJob(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(jobService.getJob(id, principal.getId())));
    }
}
