package com.vikisol.arena.feed.controller;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.feed.dto.FeedItemResponse;
import com.vikisol.arena.feed.service.FeedAggregationService;
import com.vikisol.arena.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// ARENA-MASTER-ARCHITECTURE.md PART 6 "FEED GET /feed?tab=for-you|nearby|following&cursor=" -
// the unified Home feed (PART 7.5). `nearby` deliberately delegates to the existing
// PostController's own /posts/nearby (ACTIVITY/ASK posts with a captured position only - "3
// activity mini-cards" per 7.5's right rail, not jobs/projects, so posts-only IS the correct
// behavior there, not a shortcut) rather than being reimplemented here.
// `cursor` isn't real cursor pagination yet - implemented as page/size, same convention every
// other list endpoint in this codebase already uses (see DECISIONS.md); a real opaque cursor is
// a fast-follow, not silently different from what the spec asked for without a note.
@RestController
@RequestMapping("/feed")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TALENT')")
public class FeedController {

    private final FeedAggregationService feedAggregationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<FeedItemResponse>>> getFeed(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "for-you") String tab,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(feedAggregationService.getFeed(principal.getId(), tab, page, size)));
    }
}
