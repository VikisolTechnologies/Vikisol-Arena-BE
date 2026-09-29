package com.vikisol.arena.career.controller;

import com.vikisol.arena.career.dto.CareerDtos.*;
import com.vikisol.arena.career.service.CareerService;
import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// G18-G21 (API-CHANGES.md). Owning a career profile is a talent thing; any signed-in person
// (including employers) can read a published one as their audience.
@RestController
@RequestMapping("/career")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TALENT')")
public class CareerController {

    private final CareerService careerService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CareerSelfView>> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(careerService.getMine(principal.getId())));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<CareerSelfView>> setup(@AuthenticationPrincipal UserPrincipal principal, @RequestBody SetupRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(careerService.setup(principal.getId(), request)));
    }

    @GetMapping("/me/preview")
    public ResponseEntity<ApiResponse<PrivacyPreview>> preview(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(careerService.preview(principal.getId())));
    }

    @PostMapping("/me/publish")
    public ResponseEntity<ApiResponse<CareerSelfView>> publish(
            @AuthenticationPrincipal UserPrincipal principal, @RequestBody(required = false) PublishRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(careerService.publish(principal.getId(), request == null ? null : request.openToWork())));
    }

    @PostMapping("/me/unpublish")
    public ResponseEntity<ApiResponse<CareerSelfView>> unpublish(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(careerService.unpublish(principal.getId())));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<CareerPublicView>> get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(careerService.get(principal.getId(), userId)));
    }
}
