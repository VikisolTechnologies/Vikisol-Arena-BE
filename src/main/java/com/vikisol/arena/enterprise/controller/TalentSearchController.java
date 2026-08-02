package com.vikisol.arena.enterprise.controller;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.enterprise.dto.TalentSearchResult;
import com.vikisol.arena.enterprise.service.TalentSearchService;
import com.vikisol.arena.profile.dto.CandidateProfileResponse;
import com.vikisol.arena.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/enterprise/talent")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ENTERPRISE')")
public class TalentSearchController {

    private final TalentSearchService talentSearchService;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PagedResponse<TalentSearchResult>>> search(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String text,
            @RequestParam(required = false) String industry,
            @RequestParam(defaultValue = "false") boolean remoteOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.ok(talentSearchService.search(principal.getId(), text, industry, remoteOnly, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CandidateProfileResponse>> getCandidateDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(talentSearchService.getCandidateDetail(id)));
    }

    @PostMapping("/{id}/unlock")
    public ResponseEntity<ApiResponse<Void>> unlock(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        talentSearchService.unlock(principal.getId(), id);
        return ResponseEntity.ok(ApiResponse.ok("Profile unlocked", null));
    }
}
