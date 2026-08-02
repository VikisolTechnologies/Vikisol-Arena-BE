package com.vikisol.arena.enterprise.controller;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.enterprise.service.ShortlistService;
import com.vikisol.arena.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/enterprise/shortlist")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ENTERPRISE')")
public class ShortlistController {

    private final ShortlistService shortlistService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<String>>> getShortlist(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(shortlistService.getShortlistIds(principal.getId())));
    }

    @PostMapping("/{candidateId}/toggle")
    public ResponseEntity<ApiResponse<List<String>>> toggle(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID candidateId) {
        return ResponseEntity.ok(ApiResponse.ok(shortlistService.toggle(principal.getId(), candidateId)));
    }
}
