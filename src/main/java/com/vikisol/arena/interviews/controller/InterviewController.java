package com.vikisol.arena.interviews.controller;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.interviews.dto.ConfirmSlotRequest;
import com.vikisol.arena.interviews.dto.InterviewResponse;
import com.vikisol.arena.interviews.service.InterviewService;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    @GetMapping("/by-application/{applicationId}")
    public ResponseEntity<ApiResponse<InterviewResponse>> getForApplication(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(ApiResponse.ok(interviewService.getForApplication(applicationId).orElse(null)));
    }

    @PostMapping("/propose/{applicationId}")
    public ResponseEntity<ApiResponse<InterviewResponse>> propose(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID applicationId) {
        return ResponseEntity.ok(ApiResponse.ok(interviewService.propose(principal.getId(), applicationId)));
    }

    @PutMapping("/{interviewId}/confirm")
    public ResponseEntity<ApiResponse<InterviewResponse>> confirmSlot(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID interviewId, @Valid @RequestBody ConfirmSlotRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                interviewService.confirmSlot(principal.getId(), interviewId, UUID.fromString(request.slotId()))));
    }
}
