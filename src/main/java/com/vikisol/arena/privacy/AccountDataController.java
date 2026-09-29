package com.vikisol.arena.privacy;

import com.vikisol.arena.audit.AuditActions;
import com.vikisol.arena.audit.AuditService;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

// Architect item 4: any account (a recruiter too, not only talent) can download its own entries
// in the new app's tables. Talent get the same `arena` section inside GET /profile/me/export.
@RestController
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AccountDataController {

    private final PersonalDataService personalDataService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    // Not read-only: the DATA_EXPORTED audit row is written in the same transaction.
    @GetMapping("/account/export")
    @Transactional
    public ResponseEntity<ApiResponse<AccountExport>> export(@AuthenticationPrincipal UserPrincipal principal) {
        var user = userRepository.findById(principal.getId()).orElseThrow();
        var export = new AccountExport(user.getEmail(), user.getName(), user.getRole().wireValue(), Instant.now().toString(),
                personalDataService.export(principal.getId()));
        auditService.record(null, principal.getId(), AuditActions.DATA_EXPORTED, user.getName());
        return ResponseEntity.ok(ApiResponse.ok(export));
    }

    public record AccountExport(String email, String name, String role, String exportedAt, Map<String, List<Map<String, Object>>> arena) {
    }
}
