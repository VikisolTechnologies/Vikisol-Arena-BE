package com.vikisol.arena.activities.controller;

import com.vikisol.arena.activities.dto.ActivityDtos.*;
import com.vikisol.arena.activities.entity.ActivityCatalogue;
import com.vikisol.arena.activities.service.ActivitiesService;
import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.dto.PageLimits;
import com.vikisol.arena.posts.dto.PostJoinRequestResponse;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// G7-G13 (API-CHANGES.md). {id} is the ACTIVITY post's id. Hosting and joining are talent
// actions, like /posts; reading an activity is open to guests, like GET /posts/{id}.
@RestController
@RequestMapping("/activities")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TALENT')")
public class ActivitiesController {

    private final ActivitiesService activitiesService;

    @PreAuthorize("permitAll()")
    @GetMapping("/kinds")
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> kinds() {
        return ResponseEntity.ok(ApiResponse.ok(ActivityCatalogue.catalogue()));
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ActivityResponse>> get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(activitiesService.get(id, principal == null ? null : principal.getId())));
    }

    @PutMapping("/{id}/details")
    public ResponseEntity<ApiResponse<ActivityResponse>> updateDetails(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody UpdateDetailsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(activitiesService.updateDetails(principal.getId(), id, request)));
    }

    @PostMapping(value = "/{id}/cover", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<ActivityResponse>> uploadCover(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok(activitiesService.uploadCover(principal.getId(), id, file)));
    }

    @DeleteMapping("/{id}/cover")
    public ResponseEntity<ApiResponse<ActivityResponse>> deleteCover(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(activitiesService.deleteCover(principal.getId(), id)));
    }

    @PutMapping("/{id}/questions")
    public ResponseEntity<ApiResponse<ActivityResponse>> setQuestions(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody SetQuestionsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(activitiesService.setQuestions(principal.getId(), id, request.questions())));
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<ApiResponse<PostJoinRequestResponse>> join(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody JoinRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(activitiesService.join(principal.getId(), id, request)));
    }

    @GetMapping("/{id}/answers/{userId}")
    public ResponseEntity<ApiResponse<List<AnswerResponse>>> answers(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(activitiesService.answers(principal.getId(), id, userId)));
    }

    @PostMapping("/{id}/waitlist")
    public ResponseEntity<ApiResponse<ActivityResponse>> joinWaitlist(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody(required = false) JoinRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(activitiesService.joinWaitlist(principal.getId(), id, request)));
    }

    @DeleteMapping("/{id}/waitlist")
    public ResponseEntity<ApiResponse<ActivityResponse>> leaveWaitlist(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(activitiesService.leaveWaitlist(principal.getId(), id)));
    }

    @GetMapping("/{id}/waitlist")
    public ResponseEntity<ApiResponse<List<WaitlistEntryResponse>>> waitlist(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(activitiesService.waitlist(principal.getId(), id)));
    }

    @PostMapping("/{id}/check-in")
    public ResponseEntity<ApiResponse<ActivityResponse>> checkIn(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(activitiesService.checkIn(principal.getId(), id)));
    }

    @GetMapping("/{id}/attendance")
    public ResponseEntity<ApiResponse<List<AttendanceRow>>> attendance(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(activitiesService.attendance(principal.getId(), id)));
    }

    @PostMapping("/{id}/attendance/dispute")
    public ResponseEntity<ApiResponse<ActivityResponse>> dispute(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody DisputeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(activitiesService.dispute(principal.getId(), id, request.reason())));
    }

    @PutMapping("/{id}/attendance/{joinId}/accept-dispute")
    public ResponseEntity<ApiResponse<List<AttendanceRow>>> acceptDispute(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @PathVariable UUID joinId) {
        return ResponseEntity.ok(ApiResponse.ok(activitiesService.acceptDispute(principal.getId(), id, joinId)));
    }

    @PostMapping("/{id}/feedback")
    public ResponseEntity<ApiResponse<FeedbackResponse>> giveFeedback(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody FeedbackRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(activitiesService.giveFeedback(principal.getId(), id, request)));
    }

    // Flow §3 A12: the host marks who came.
    @PutMapping("/{id}/attendance/{joinId}/check-in")
    public ResponseEntity<ApiResponse<List<AttendanceRow>>> hostCheckIn(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @PathVariable UUID joinId) {
        return ResponseEntity.ok(ApiResponse.ok(activitiesService.hostCheckIn(principal.getId(), id, joinId)));
    }

    // Flow §3 A13 / row 25: the joiner confirms (or disputes) attendance.
    @PostMapping("/{id}/attendance/confirm")
    public ResponseEntity<ApiResponse<ActivityResponse>> confirmAttendance(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody ConfirmAttendanceRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(activitiesService.confirmAttendance(principal.getId(), id, request)));
    }

    // Flow §3 (Trekking): host-only, approved joiners only.
    @GetMapping("/{id}/emergency-contacts")
    public ResponseEntity<ApiResponse<List<EmergencyContactResponse>>> emergencyContacts(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(activitiesService.emergencyContacts(principal.getId(), id)));
    }


    @GetMapping("/feedback/received")
    public ResponseEntity<ApiResponse<List<FeedbackResponse>>> receivedFeedback(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size) {
        return PageLimits.ok(activitiesService.receivedFeedback(principal.getId(), PageLimits.of(page, size)));
    }
}
