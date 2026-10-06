package com.vikisol.arena.needs.controller;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.dto.PageLimits;
import com.vikisol.arena.needs.dto.NeedDtos.*;
import com.vikisol.arena.needs.entity.NeedCategory;
import com.vikisol.arena.needs.service.NeedService;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

// G14-G17 (API-CHANGES.md). {id} is an ASK (need) or OFFER post, created as today with POST /posts.
@RestController
@RequestMapping("/needs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TALENT')")
public class NeedController {

    private final NeedService needService;

    @PreAuthorize("permitAll()")
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<String>>> categories() {
        return ResponseEntity.ok(ApiResponse.ok(Arrays.stream(NeedCategory.values()).map(NeedCategory::wireValue).toList()));
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NeedView>> get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(needService.get(id, principal == null ? null : principal.getId())));
    }

    @PutMapping("/{id}/details")
    public ResponseEntity<ApiResponse<NeedView>> setDetails(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody DetailsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(needService.setDetails(principal.getId(), id, request)));
    }

    @PostMapping("/{id}/responses")
    public ResponseEntity<ApiResponse<ResponseView>> respond(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody RespondRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(needService.respond(principal.getId(), id, request.message())));
    }

    @GetMapping("/{id}/responses")
    public ResponseEntity<ApiResponse<List<ResponseView>>> responses(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(needService.responses(principal.getId(), id)));
    }

    @DeleteMapping("/{id}/responses/me")
    public ResponseEntity<ApiResponse<ResponseView>> withdraw(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(needService.withdraw(principal.getId(), id)));
    }

    @PutMapping("/{id}/responses/{responseId}/accept")
    public ResponseEntity<ApiResponse<ResponseView>> accept(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @PathVariable UUID responseId) {
        return ResponseEntity.ok(ApiResponse.ok(needService.accept(principal.getId(), id, responseId)));
    }

    @PutMapping("/{id}/responses/{responseId}/decline")
    public ResponseEntity<ApiResponse<ResponseView>> decline(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @PathVariable UUID responseId) {
        return ResponseEntity.ok(ApiResponse.ok(needService.decline(principal.getId(), id, responseId)));
    }

    @PostMapping("/{id}/responses/{responseId}/confirm")
    public ResponseEntity<ApiResponse<ResponseView>> confirm(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @PathVariable UUID responseId,
            @Valid @RequestBody(required = false) ConfirmRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(needService.confirm(principal.getId(), id, responseId, request == null ? null : request.note())));
    }

    @GetMapping("/responses/mine")
    public ResponseEntity<ApiResponse<List<MyResponseView>>> mine(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size) {
        return PageLimits.ok(needService.myResponses(principal.getId(), PageLimits.of(page, size)));
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/outcomes/{userId}")
    public ResponseEntity<ApiResponse<List<OutcomeView>>> outcomes(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID userId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size) {
        return PageLimits.ok(needService.outcomes(userId, principal == null ? null : principal.getId(), PageLimits.of(page, size)));
    }
}
