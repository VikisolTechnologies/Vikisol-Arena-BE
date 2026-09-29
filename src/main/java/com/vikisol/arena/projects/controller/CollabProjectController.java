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
