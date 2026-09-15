package com.vikisol.arena.seed;

import com.vikisol.arena.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ARENA-WEB-AND-SEED.md Part 4 - "one documented command creates it, one documented command
 * removes it completely." The two commands:
 *
 *   curl -X POST   https://api-arena.vikisol.in/api/v1/admin/demo-content -H "Authorization: Bearer <platform_admin token>"
 *   curl -X DELETE https://api-arena.vikisol.in/api/v1/admin/demo-content -H "Authorization: Bearer <platform_admin token>"
 *
 * Gated at the CLASS level (not just each method) behind arena.demo-content.enabled
 * (ARENA_SEED_MODE) - with the flag off, this whole controller bean never registers, so both
 * routes genuinely 404 rather than existing-but-refusing. That's what makes "verify the
 * production build with the flag off shows zero seed records" checkable by more than just
 * trusting the code: with the flag off there is no code path capable of writing a seed record at
 * all. @PreAuthorize is the second, independent gate - even with the flag on, only a
 * platform_admin session can call either endpoint.
 */
@RestController
@RequestMapping("/admin/demo-content")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@ConditionalOnProperty(value = "arena.demo-content.enabled", havingValue = "true")
public class DemoContentController {

    private final DemoContentService demoContentService;

    @PostMapping
    public ResponseEntity<ApiResponse<DemoContentService.SeedSummary>> seed() {
        return ResponseEntity.ok(ApiResponse.ok("Demo content seeded", demoContentService.seed()));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<DemoContentService.RemovalSummary>> removeAll() {
        return ResponseEntity.ok(ApiResponse.ok("Demo content removed", demoContentService.removeAll()));
    }
}
