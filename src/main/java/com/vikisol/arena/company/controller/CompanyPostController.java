package com.vikisol.arena.company.controller;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.posts.dto.CreateCompanyPostRequest;
import com.vikisol.arena.posts.dto.PostResponse;
import com.vikisol.arena.posts.service.PostService;
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

// ARENA-V2-PRODUCT-ARCHITECTURE.md §3.5/§6 "Company posts appear in the feed... gives
// enterprises a reason to be here between hires" - separate from CompanyController
// (talent-facing browse, @PreAuthorize hasRole('TALENT')) since posting is the opposite role
// entirely. Same @PreAuthorize convention as every other enterprise-workspace controller
// (EnterpriseProfileController, JobPostingController, ...).
@RestController
@RequestMapping("/companies/me/posts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('RECRUITER','COMPANY_ADMIN')")
public class CompanyPostController {

    private final PostService postService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<PostResponse>>> getMyCompanyPosts(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.ok(postService.getCompanyPosts(principal.getId(), pageable)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PostResponse>> createCompanyPost(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody CreateCompanyPostRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Post published", postService.createCompanyPost(principal.getId(), request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCompanyPost(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        postService.deleteCompanyPost(principal.getId(), id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
