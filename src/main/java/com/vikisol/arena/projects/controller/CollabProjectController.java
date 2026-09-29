package com.vikisol.arena.projects.controller;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.dto.PageLimits;
import com.vikisol.arena.posts.dto.PostJoinRequestResponse;
import com.vikisol.arena.projects.dto.ProjectDtos.*;
import com.vikisol.arena.projects.service.CollabProjectService;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// G29-G32 (API-CHANGES.md). {id} is a COLLAB post created with POST /posts {"intentType":"collab"}.
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('TALENT')")
public class CollabProjectController {

    private final CollabProjectService projectService;

    @PreAuthorize("permitAll()")
    @GetMapping("/projects/{id}")
    public ResponseEntity<ApiResponse<ProjectView>> get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.get(id, principal == null ? null : principal.getId())));
    }

    // Row 26 / flow §7.
    @PostMapping("/projects")
    public ResponseEntity<ApiResponse<ProjectView>> create(@AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody CreateProjectRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Project published", projectService.create(principal.getId(), request)));
    }

    @PostMapping("/projects/{id}/applications")
    public ResponseEntity<ApiResponse<PostJoinRequestResponse>> apply(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody ApplicationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.apply(principal.getId(), id, request)));
    }

    @PostMapping(value = "/projects/{id}/cover", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<ProjectView>> uploadCover(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.uploadCover(principal.getId(), id, file)));
    }

    @DeleteMapping("/projects/{id}/cover")
    public ResponseEntity<ApiResponse<ProjectView>> deleteCover(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.deleteCover(principal.getId(), id)));
    }

    @GetMapping("/projects/{id}/milestones")
    public ResponseEntity<ApiResponse<List<MilestoneView>>> milestones(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.milestones(principal.getId(), id)));
    }

    @PostMapping("/projects/{id}/milestones")
    public ResponseEntity<ApiResponse<List<MilestoneView>>> addMilestone(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody MilestoneInput request) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.addMilestone(principal.getId(), id, request.title())));
    }

    @PutMapping("/projects/{id}/milestones/{milestoneId}")
    public ResponseEntity<ApiResponse<List<MilestoneView>>> updateMilestone(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @PathVariable UUID milestoneId,
            @Valid @RequestBody MilestoneUpdate request) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.updateMilestone(principal.getId(), id, milestoneId, request)));
    }

    @DeleteMapping("/projects/{id}/milestones/{milestoneId}")
    public ResponseEntity<ApiResponse<List<MilestoneView>>> deleteMilestone(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @PathVariable UUID milestoneId) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.deleteMilestone(principal.getId(), id, milestoneId)));
    }

    @PostMapping("/projects/{id}/complete")
    public ResponseEntity<ApiResponse<ProjectView>> complete(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody CompleteRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.complete(principal.getId(), id, request)));
    }

    @PutMapping("/projects/{id}/roles")
    public ResponseEntity<ApiResponse<ProjectView>> setRoles(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody RolesRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.setRoles(principal.getId(), id, request.roles())));
    }

    @PostMapping("/projects/{id}/join")
    public ResponseEntity<ApiResponse<PostJoinRequestResponse>> join(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody JoinRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.join(principal.getId(), id, request)));
    }

    @GetMapping("/projects/{id}/requests")
    public ResponseEntity<ApiResponse<List<RequestView>>> requests(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.requests(principal.getId(), id)));
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/projects/of/{userId}")
    public ResponseEntity<ApiResponse<List<ProjectCard>>> projectsOf(
            @PathVariable UUID userId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size) {
        return PageLimits.ok(projectService.projectsOf(userId, PageLimits.of(page, size)));
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/profile/{userId}/stats")
    public ResponseEntity<ApiResponse<ProfileStats>> stats(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(projectService.stats(userId)));
    }
}
