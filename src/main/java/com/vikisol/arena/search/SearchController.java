package com.vikisol.arena.search;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// GET /search?q=basketball&type=all|activities|discussions|jobs|projects|companies&limit=20
// Open to guests, like every list it searches - a signed-in viewer additionally sees
// followers-only posts from people they follow and personalised job match scores.
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @PreAuthorize("permitAll()")
    @GetMapping
    public ResponseEntity<ApiResponse<SearchResponse>> search(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(defaultValue = "20") int limit) {
        int bounded = Math.max(1, Math.min(limit, 50));
        return ResponseEntity.ok(ApiResponse.ok(searchService.search(q, type, bounded, principal == null ? null : principal.getId())));
    }
}
