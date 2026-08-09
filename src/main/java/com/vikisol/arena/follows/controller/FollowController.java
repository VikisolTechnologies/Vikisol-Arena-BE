package com.vikisol.arena.follows.controller;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.follows.dto.FollowCountsResponse;
import com.vikisol.arena.follows.dto.FollowerResponse;
import com.vikisol.arena.follows.service.FollowService;
import com.vikisol.arena.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/follows")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TALENT')")
public class FollowController {

    private final FollowService followService;

    @PostMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> follow(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID userId) {
        followService.follow(principal.getId(), userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> unfollow(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID userId) {
        followService.unfollow(principal.getId(), userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/{userId}/counts")
    public ResponseEntity<ApiResponse<FollowCountsResponse>> getCounts(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(followService.getCounts(userId, principal.getId())));
    }

    @GetMapping("/me/followers")
    public ResponseEntity<ApiResponse<List<FollowerResponse>>> getMyFollowers(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(followService.getFollowers(principal.getId())));
    }

    @GetMapping("/me/following")
    public ResponseEntity<ApiResponse<List<FollowerResponse>>> getMyFollowing(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(followService.getFollowing(principal.getId())));
    }

    // Phase C company-follow.
    @PostMapping("/company/{companyId}")
    public ResponseEntity<ApiResponse<Void>> followCompany(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        followService.followCompany(principal.getId(), companyId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/company/{companyId}")
    public ResponseEntity<ApiResponse<Void>> unfollowCompany(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        followService.unfollowCompany(principal.getId(), companyId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
