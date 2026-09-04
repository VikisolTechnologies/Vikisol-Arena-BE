package com.vikisol.arena.landing.controller;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.landing.dto.LandingStatsResponse;
import com.vikisol.arena.landing.service.LandingService;
import com.vikisol.arena.marketplace.dto.ProjectResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Unauthenticated by design (see SecurityConfig's GET "/public/**" permitAll) - feeds the
 *  logged-out marketing homepage only. Never expose anything here that isn't already visible to
 *  any authenticated user through the equivalent real endpoint (ProjectController et al) -
 *  ProjectMapper's bid/project shape carries no PII beyond the display name every signed-in user
 *  already sees browsing the real marketplace. */
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class LandingController {

    private final LandingService landingService;

    @GetMapping("/landing-stats")
    public ResponseEntity<ApiResponse<LandingStatsResponse>> stats() {
        return ResponseEntity.ok(ApiResponse.ok(landingService.getStats()));
    }

    @GetMapping("/landing-featured-project")
    public ResponseEntity<ApiResponse<ProjectResponse>> featuredProject() {
        return ResponseEntity.ok(ApiResponse.ok(landingService.getFeaturedProject()));
    }
}
