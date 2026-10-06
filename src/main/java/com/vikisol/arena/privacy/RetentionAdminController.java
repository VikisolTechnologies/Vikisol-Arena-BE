package com.vikisol.arena.privacy;

import com.vikisol.arena.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// The candidate-retention dry-run report (counts only). Read-only: it never deletes, whatever
// the flag says. Under /admin, so the platform-admin role and the admin 2FA filter apply.
@RestController
@RequestMapping("/admin/retention")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class RetentionAdminController {

    private final CandidateRetentionService retentionService;

    @GetMapping
    public ResponseEntity<ApiResponse<CandidateRetentionService.Report>> report() {
        return ResponseEntity.ok(ApiResponse.ok(retentionService.report()));
    }
}
