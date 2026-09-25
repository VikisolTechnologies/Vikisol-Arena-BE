package com.vikisol.arena.communities.controller;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.communities.dto.*;
import com.vikisol.arena.communities.service.CommunityService;
import com.vikisol.arena.posts.dto.PostResponse;
import com.vikisol.arena.posts.service.PostService;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Arena restructure Phase 2 (Discuss). Reading is open to guests (like the rest of Discuss);
// anything that changes something needs a talent account, same as posting.
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('TALENT')")
public class CommunityController {

    private final CommunityService communityService;
    private final PostService postService;

    private static UUID viewer(UserPrincipal principal) {
        return principal == null ? null : principal.getId();
    }

    // All of Discuss (no community) or one community's threads: sort=new|top.
    @PreAuthorize("permitAll()")
    @GetMapping("/discuss/threads")
    public ResponseEntity<ApiResponse<List<PostResponse>>> threads(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String community,
            @RequestParam(defaultValue = "new") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        int bounded = Math.max(1, Math.min(size, 100));
        return ResponseEntity.ok(ApiResponse.ok(community == null || community.isBlank()
                ? postService.listDiscussions(viewer(principal), null, sort, page, bounded)
                : communityService.posts(community, sort, page, bounded, viewer(principal))));
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/communities")
    public ResponseEntity<ApiResponse<List<CommunityResponse>>> list(
            @AuthenticationPrincipal UserPrincipal principal, @RequestParam(defaultValue = "") String q) {
        return ResponseEntity.ok(ApiResponse.ok(communityService.list(q, viewer(principal))));
    }

    @GetMapping("/communities/mine")
    public ResponseEntity<ApiResponse<List<CommunityResponse>>> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(communityService.mine(principal.getId())));
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/communities/{slug}")
    public ResponseEntity<ApiResponse<CommunityResponse>> get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.ok(communityService.get(slug, viewer(principal))));
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/communities/{slug}/moderators")
    public ResponseEntity<ApiResponse<List<CommunityMemberResponse>>> moderators(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.ok(communityService.moderators(slug)));
    }

    @PostMapping("/communities")
    public ResponseEntity<ApiResponse<CommunityResponse>> create(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody CreateCommunityRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(communityService.create(principal.getId(), request)));
    }

    @PatchMapping("/communities/{slug}")
    public ResponseEntity<ApiResponse<CommunityResponse>> update(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable String slug, @Valid @RequestBody UpdateCommunityRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(communityService.update(slug, principal.getId(), request)));
    }

    @PostMapping("/communities/{slug}/join")
    public ResponseEntity<ApiResponse<CommunityResponse>> join(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.ok(communityService.join(slug, principal.getId())));
    }

    @DeleteMapping("/communities/{slug}/join")
    public ResponseEntity<ApiResponse<CommunityResponse>> leave(@AuthenticationPrincipal UserPrincipal principal, @PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.ok(communityService.leave(slug, principal.getId())));
    }

    // Owner: {"value": true} makes the member a moderator, false makes them a member again.
    @PutMapping("/communities/{slug}/members/{userId}/moderator")
    public ResponseEntity<ApiResponse<CommunityMemberResponse>> setModerator(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable String slug, @PathVariable UUID userId,
            @Valid @RequestBody ModerationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(communityService.setModerator(slug, principal.getId(), userId, Boolean.TRUE.equals(request.value()))));
    }

    // Owner/moderator: {"value": true} bans, false unbans.
    @PutMapping("/communities/{slug}/members/{userId}/ban")
    public ResponseEntity<ApiResponse<CommunityMemberResponse>> ban(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable String slug, @PathVariable UUID userId,
            @Valid @RequestBody ModerationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(communityService.ban(slug, principal.getId(), userId, Boolean.TRUE.equals(request.value()))));
    }

    // Owner/moderator: take a post down from the community, with an optional reason.
    @PostMapping("/communities/{slug}/posts/{postId}/remove")
    public ResponseEntity<ApiResponse<Void>> removePost(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable String slug, @PathVariable UUID postId,
            @Valid @RequestBody(required = false) ModerationRequest request) {
        communityService.removePost(slug, principal.getId(), postId, request == null ? null : request.reason());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
