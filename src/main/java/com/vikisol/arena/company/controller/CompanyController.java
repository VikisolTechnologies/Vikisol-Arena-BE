package com.vikisol.arena.company.controller;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.company.dto.CompanyResponse;
import com.vikisol.arena.company.service.CompanyService;
import com.vikisol.arena.jobs.dto.JobResponse;
import com.vikisol.arena.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/companies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TALENT')")
public class CompanyController {

    private final CompanyService companyService;

    // "Enter as guest" - Companies must be browsable before signup; overrides the class-level
    // hasRole('TALENT'). companyService.listCompanies already treats a null viewingUserId as
    // anonymous (viewerFollows: null), same as getCompany below.
    @PreAuthorize("permitAll()")
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<CompanyResponse>>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        UUID viewerId = principal == null ? null : principal.getId();
        return ResponseEntity.ok(ApiResponse.ok(companyService.listCompanies(query, viewerId, pageable)));
    }

    // ARENA-INVENTORY-FIXES.md FIX 1 - a shared company link is the growth loop, so this must
    // render logged-out too; overrides the class-level hasRole('TALENT'). companyService's
    // toResponse() already treats a null viewingUserId as "anonymous" (viewerFollows: null).
    @PreAuthorize("permitAll()")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CompanyResponse>> get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        UUID viewerId = principal == null ? null : principal.getId();
        return ResponseEntity.ok(ApiResponse.ok(companyService.getCompany(id, viewerId)));
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/{id}/jobs")
    public ResponseEntity<ApiResponse<PagedResponse<JobResponse>>> getJobs(
            @PathVariable UUID id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.ok(companyService.getCompanyJobs(id, pageable)));
    }
}
