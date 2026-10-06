package com.vikisol.arena.connect;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.dto.PageLimits;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// FE-API-GAPS row 34.
@RestController
@RequiredArgsConstructor
public class ConnectController {

    private final ConnectService connectService;

    @PreAuthorize("hasAnyRole('RECRUITER','COMPANY_ADMIN')")
    @PostMapping("/enterprise/talent/{candidateId}/connect")
    public ResponseEntity<ApiResponse<ConnectService.ConnectView>> send(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID candidateId, @Valid @RequestBody SendRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(connectService.send(principal.getId(), candidateId, request.jobId(), request.note())));
    }

    public record SendRequest(UUID jobId, @NotBlank(message = "is required") @Size(max = 300, message = "must be at most 300 characters") String note) {
    }

    @PreAuthorize("hasRole('TALENT')")
    @GetMapping("/connect-requests")
    public ResponseEntity<ApiResponse<List<ConnectService.ConnectView>>> mine(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageLimits.ok(connectService.mine(principal.getId(), PageLimits.of(page, size)));
    }

    @PreAuthorize("hasRole('TALENT')")
    @PostMapping("/connect-requests/{id}/accept")
    public ResponseEntity<ApiResponse<ConnectService.ConnectView>> accept(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(connectService.decide(principal.getId(), id, true)));
    }

    @PreAuthorize("hasRole('TALENT')")
    @PostMapping("/connect-requests/{id}/decline")
    public ResponseEntity<ApiResponse<ConnectService.ConnectView>> decline(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(connectService.decide(principal.getId(), id, false)));
    }
}
