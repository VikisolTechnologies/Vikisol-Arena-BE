package com.vikisol.arena.posts.controller;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.posts.dto.CreateCommentRequest;
import com.vikisol.arena.posts.dto.CreatePostRequest;
import com.vikisol.arena.posts.dto.PostCommentResponse;
import com.vikisol.arena.posts.dto.PostJoinRequestResponse;
import com.vikisol.arena.posts.dto.PostResponse;
import com.vikisol.arena.posts.dto.ReportPostRequest;
import com.vikisol.arena.posts.service.PostCommentService;
import com.vikisol.arena.posts.service.PostReactionService;
import com.vikisol.arena.posts.service.PostService;
import com.vikisol.arena.platform.service.ModerationService;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TALENT')")
public class PostController {

    private final PostService postService;
    private final PostCommentService postCommentService;
    private final PostReactionService postReactionService;
    private final ModerationService moderationService;

    // "Enter as guest" - Home's feed must render before signup; postService.getFeed already
    // treats a null viewingUserId as anonymous (empty follow-set, no personalized interest
    // vector) - overrides the class-level hasRole('TALENT'), same pattern as by-user below.
    @PreAuthorize("permitAll()")
    @GetMapping("/feed")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getFeed(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID viewerId = principal == null ? null : principal.getId();
        return ResponseEntity.ok(ApiResponse.ok(postService.getFeed(viewerId, page, size)));
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/trending")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getTrending(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID viewerId = principal == null ? null : principal.getId();
        return ResponseEntity.ok(ApiResponse.ok(postService.getTrending(viewerId, page, size)));
    }

    // ARENA-INVENTORY-FIXES.md FIX 1 - the public profile page's "Activity" section needs this
    // logged-out; overrides the class-level hasRole('TALENT'). postService.getUserPosts already
    // treats a null viewingUserId as "anonymous" (no self/follower-only posts leak through).
    @PreAuthorize("permitAll()")
    @GetMapping("/by-user/{userId}")
    public ResponseEntity<ApiResponse<PagedResponse<PostResponse>>> getUserPosts(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        UUID viewerId = principal == null ? null : principal.getId();
        return ResponseEntity.ok(ApiResponse.ok(postService.getUserPosts(userId, viewerId, pageable)));
    }

    // Map page must render before signup too - postService.getNearby is a plain geo+time query,
    // no personalization to degrade (see its own null-tolerant signature).
    @PreAuthorize("permitAll()")
    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getNearby(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam double lat, @RequestParam double lng,
            @RequestParam(defaultValue = "5") double radiusKm,
            @RequestParam(required = false) Integer withinHours,
            @RequestParam(required = false) String intentType) {
        UUID viewerId = principal == null ? null : principal.getId();
        return ResponseEntity.ok(ApiResponse.ok(postService.getNearby(viewerId, lat, lng, radiusKm, withinHours, intentType)));
    }

    // ARENA-STABILIZE.md Phase 2, G9 - shared post links (the app's growth loop, same reasoning
    // as ARENA-INVENTORY-FIXES.md FIX 1) need to work logged-out. postService.getPost's whole
    // call chain is already null-viewer-tolerant (see PostMapper) - overrides the class-level
    // hasRole('TALENT').
    @PreAuthorize("permitAll()")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PostResponse>> getPost(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        UUID viewerId = principal == null ? null : principal.getId();
        return ResponseEntity.ok(ApiResponse.ok(postService.getPost(id, viewerId)));
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<PagedResponse<PostResponse>>> getMyPosts(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.ok(postService.getMyPosts(principal.getId(), pageable)));
    }

    // PART 6 SAVE - kept under /posts (where every other post-interaction endpoint already
    // lives) rather than the spec's literal /me/saved, same "documented small path deviation"
    // precedent as this codebase's other spec reconciliations (see DECISIONS.md).
    @GetMapping("/saved")
    public ResponseEntity<ApiResponse<PagedResponse<PostResponse>>> getSaved(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.ok(postService.getSaved(principal.getId(), pageable)));
    }

    @PostMapping("/{id}/save")
    public ResponseEntity<ApiResponse<Void>> save(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        postService.save(principal.getId(), id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/{id}/save")
    public ResponseEntity<ApiResponse<Void>> unsave(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        postService.unsave(principal.getId(), id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PostResponse>> create(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody CreatePostRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Post published", postService.create(principal.getId(), request)));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<PostResponse>> cancel(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(postService.cancel(principal.getId(), id)));
    }

    // ARENA-FIX-EVERYTHING.md Phase 1 finding - see PostService.delete()'s own comment for why
    // this is narrower than cancel() (refuses on real history worth preserving) rather than a
    // second way to do the same thing.
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        postService.delete(principal.getId(), id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/{id}/joins")
    public ResponseEntity<ApiResponse<PostJoinRequestResponse>> requestJoin(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(postService.requestJoin(principal.getId(), id)));
    }

    @GetMapping("/{id}/joins")
    public ResponseEntity<ApiResponse<List<PostJoinRequestResponse>>> getJoinRequests(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(postService.getJoinRequests(principal.getId(), id)));
    }

    @PutMapping("/{id}/joins/{joinId}/approve")
    public ResponseEntity<ApiResponse<PostJoinRequestResponse>> approveJoin(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @PathVariable UUID joinId) {
        return ResponseEntity.ok(ApiResponse.ok(postService.decideJoin(principal.getId(), id, joinId, true)));
    }

    @DeleteMapping("/{id}/joins/me")
    public ResponseEntity<ApiResponse<PostJoinRequestResponse>> withdrawJoin(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(postService.withdrawJoin(principal.getId(), id)));
    }

    @PutMapping("/{id}/joins/{joinId}/outcome")
    public ResponseEntity<ApiResponse<PostJoinRequestResponse>> recordOutcome(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @PathVariable UUID joinId,
            @RequestBody OutcomeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(postService.recordOutcome(principal.getId(), id, joinId, request.outcome())));
    }

    public record OutcomeRequest(String outcome) {}

    @PutMapping("/{id}/joins/{joinId}/decline")
    public ResponseEntity<ApiResponse<PostJoinRequestResponse>> declineJoin(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @PathVariable UUID joinId) {
        return ResponseEntity.ok(ApiResponse.ok(postService.decideJoin(principal.getId(), id, joinId, false)));
    }

    // ARENA-V2-PRODUCT-ARCHITECTURE.md Phase C - comments/reactions.
    // ARENA-STABILIZE.md Phase 2, G9 - a logged-out visitor to a shared post link should see
    // its comment thread too, same as the post itself.
    @PreAuthorize("permitAll()")
    @GetMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<List<PostCommentResponse>>> getComments(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(postCommentService.getComments(id, principal == null ? null : principal.getId())));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<PostCommentResponse>> addComment(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody CreateCommentRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(postCommentService.addComment(principal.getId(), id, request.content(), request.parentCommentId(),
                Boolean.TRUE.equals(request.anonymous()))));
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @PathVariable UUID commentId) {
        postCommentService.deleteComment(principal.getId(), id, commentId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/{id}/react")
    public ResponseEntity<ApiResponse<Void>> react(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        postReactionService.react(principal.getId(), id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // Phase 2 (Discuss) - {"value": 1 | -1 | 0}.
    @PutMapping("/{id}/vote")
    public ResponseEntity<ApiResponse<Void>> vote(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @RequestBody VoteRequest request) {
        postReactionService.vote(principal.getId(), id, request.value());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    public record VoteRequest(int value) {
    }

    @DeleteMapping("/{id}/react")
    public ResponseEntity<ApiResponse<Void>> unreact(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        postReactionService.unreact(principal.getId(), id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // §4 safety-audit fix: "report ... everywhere" - posts specifically were previously only
    // reportable via a Room, which meant UPDATE posts and not-yet-joined ACTIVITY/ASK posts
    // had no report path at all. See SAFETY-STATUS.md.
    @PostMapping("/{id}/report")
    public ResponseEntity<ApiResponse<Void>> report(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody ReportPostRequest request) {
        moderationService.filePostReport(principal.getId(), id, request.reason());
        return ResponseEntity.ok(ApiResponse.ok("Report submitted", null));
    }
}
