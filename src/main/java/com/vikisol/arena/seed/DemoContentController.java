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
 * Gated at the CLASS level (not just each method) behind app.demo-content.enabled
 * (ARENA_SEED_MODE, see application.yml - nested under `app:`, same parent as `app.seed`) -
 * with the flag off, this whole controller bean never registers, so both routes genuinely 404
 * rather than existing-but-refusing. That's what makes "verify the production build with the
 * flag off shows zero seed records" checkable by more than just trusting the code: with the
 * flag off there is no code path capable of writing a seed record at all. @PreAuthorize is the
 * second, independent gate - even with the flag on, only a platform_admin session can call
 * either endpoint.
 *
 * Live-verified 2026-09-15: with the property path wrong (this class briefly shipped with
 * "arena.demo-content.enabled" - a plausible-looking but wrong parent key, never matching the
 * yml's actual `app.demo-content.enabled`), the flag silently never took effect. That failure
 * mode is invisible from the outside - Spring just never registers the bean, same as the
 * intended off-state - so it was only caught by checking Railway logs for the specific
 * NoResourceFoundException after ARENA_SEED_MODE=true was confirmed set, not by the app failing
 * to start or logging anything about it.
 */
@RestController
@RequestMapping("/admin/demo-content")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@ConditionalOnProperty(value = "app.demo-content.enabled", havingValue = "true")
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
