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
            @RequestParam(defaultValue = "20") int limit,
            // Row 17: people search near a point ("lat,lng") within radiusKm (default 5, max 50).
            @RequestParam(required = false) String near,
            @RequestParam(required = false) Double radiusKm) {
        int bounded = Math.max(1, Math.min(limit, 50));
        Double lat = null, lng = null;
        if (near != null && !near.isBlank()) {
            String[] parts = near.split(",");
            try {
                lat = Double.parseDouble(parts[0].trim());
                lng = Double.parseDouble(parts[1].trim());
            } catch (RuntimeException e) {
                throw new com.vikisol.arena.common.exception.BadRequestException("near must be lat,lng");
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(searchService.search(q, type, bounded, principal == null ? null : principal.getId(), lat, lng, radiusKm)));
    }
}
