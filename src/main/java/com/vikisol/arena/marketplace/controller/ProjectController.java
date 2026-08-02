package com.vikisol.arena.marketplace.controller;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.marketplace.dto.*;
import com.vikisol.arena.marketplace.service.ProjectService;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/marketplace")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping("/projects")
    public ResponseEntity<ApiResponse<PagedResponse<ProjectResponse>>> getProjects(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.ok(projectService.getOpenProjects(pageable, principal.getId())));
    }

    @GetMapping("/projects/{id}")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProject(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.getProject(id, principal.getId())));
    }

    @PostMapping("/projects")
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody CreateProjectRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Project published", projectService.createProject(principal.getId(), request)));
    }

    @GetMapping("/my-projects")
    public ResponseEntity<ApiResponse<PagedResponse<ProjectResponse>>> getMyProjects(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.ok(projectService.getMyProjects(principal.getId(), pageable)));
    }

    @GetMapping("/my-bids")
    public ResponseEntity<ApiResponse<PagedResponse<BidResponse>>> getMyBids(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.ok(projectService.getMyBids(principal.getId(), pageable)));
    }

    @PostMapping("/projects/{id}/bids")
    public ResponseEntity<ApiResponse<BidResponse>> placeBid(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody PlaceBidRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Bid placed", projectService.placeBid(principal.getId(), id, request.amount())));
    }

    @PostMapping("/projects/{id}/award")
    public ResponseEntity<ApiResponse<ProjectResponse>> award(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody AwardRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.award(principal.getId(), id, UUID.fromString(request.bidId()))));
    }

    @PostMapping("/milestones/{milestoneId}/deliverables")
    public ResponseEntity<ApiResponse<MilestoneResponse>> submitDeliverable(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID milestoneId,
            @Valid @RequestBody SubmitDeliverableRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.submitDeliverable(principal.getId(), milestoneId, request)));
    }

    @PutMapping("/milestones/{milestoneId}/accept")
    public ResponseEntity<ApiResponse<MilestoneResponse>> acceptMilestone(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID milestoneId) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.reviewMilestone(principal.getId(), milestoneId, true)));
    }

    @PutMapping("/milestones/{milestoneId}/reject")
    public ResponseEntity<ApiResponse<MilestoneResponse>> rejectMilestone(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID milestoneId) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.reviewMilestone(principal.getId(), milestoneId, false)));
    }

    @PostMapping("/projects/{id}/ratings")
    public ResponseEntity<ApiResponse<Void>> rate(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody RateRequest request) {
        projectService.rate(principal.getId(), id, request);
        return ResponseEntity.ok(ApiResponse.ok("Rating submitted", null));
    }
}
