package com.vikisol.arena.interviews.controller;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.interviews.dto.AssignHiringManagerRequest;
import com.vikisol.arena.interviews.dto.ConfirmSlotRequest;
import com.vikisol.arena.interviews.dto.HiringManagerInterviewResponse;
import com.vikisol.arena.interviews.dto.InterviewResponse;
import com.vikisol.arena.interviews.dto.SubmitInterviewFeedbackRequest;
import com.vikisol.arena.interviews.dto.UpdateInterviewNotesRequest;
import com.vikisol.arena.interviews.service.InterviewService;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    @GetMapping("/by-application/{applicationId}")
    public ResponseEntity<ApiResponse<InterviewResponse>> getForApplication(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID applicationId) {
        return ResponseEntity.ok(ApiResponse.ok(interviewService.getForApplication(principal.getId(), applicationId).orElse(null)));
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

    // Open to either participant (candidate or enterprise) - mirrors InterviewRoom.tsx, where the
    // notes textarea is shared, only "End & give feedback" is enterprise-gated. Participant-ness
    // is enforced in InterviewService.saveNotes() the same way propose()/confirmSlot() already do.
    @PutMapping("/{interviewId}/notes")
    public ResponseEntity<ApiResponse<InterviewResponse>> saveNotes(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID interviewId, @Valid @RequestBody UpdateInterviewNotesRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(interviewService.saveNotes(principal.getId(), interviewId, request.notes())));
    }

    // Enterprise-side only, matching arena-web's canGiveFeedback (always false on the candidate
    // route) - includes HIRING_MANAGER per ARENA-ENTERPRISE-SUITE.md HM2 (they submit structured
    // feedback on their assigned interviews, nothing else enterprise-side). Applied at the method
    // level since this controller otherwise serves both talent and enterprise roles.
    @PostMapping("/{interviewId}/feedback")
    @PreAuthorize("hasAnyRole('RECRUITER','COMPANY_ADMIN','HIRING_MANAGER')")
    public ResponseEntity<ApiResponse<InterviewResponse>> submitFeedback(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID interviewId, @Valid @RequestBody SubmitInterviewFeedbackRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(interviewService.submitFeedback(principal.getId(), interviewId, request)));
    }

    // HM1: "My interviews" - only ones assigned to the caller.
    @GetMapping("/mine")
    @PreAuthorize("hasRole('HIRING_MANAGER')")
    public ResponseEntity<ApiResponse<List<HiringManagerInterviewResponse>>> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(interviewService.getMyAssignedInterviews(principal.getId())));
    }

    @GetMapping("/mine/{interviewId}")
    @PreAuthorize("hasRole('HIRING_MANAGER')")
    public ResponseEntity<ApiResponse<HiringManagerInterviewResponse>> mineDetail(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID interviewId) {
        return ResponseEntity.ok(ApiResponse.ok(interviewService.getMyAssignedInterview(principal.getId(), interviewId)));
    }

    // HM3: a recruiter/company_admin assigns a hiring manager when scheduling.
    @PutMapping("/{interviewId}/assign-hiring-manager")
    @PreAuthorize("hasAnyRole('RECRUITER','COMPANY_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> assignHiringManager(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID interviewId, @Valid @RequestBody AssignHiringManagerRequest request) {
        interviewService.assignHiringManager(principal.getId(), interviewId, UUID.fromString(request.hiringManagerUserId()));
        return ResponseEntity.ok(ApiResponse.ok("Hiring manager assigned", null));
    }
}
