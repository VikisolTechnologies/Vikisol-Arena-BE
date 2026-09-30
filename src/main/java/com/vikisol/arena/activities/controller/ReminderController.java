package com.vikisol.arena.activities.controller;

import com.vikisol.arena.activities.dto.ActivityDtos.ReminderRequest;
import com.vikisol.arena.activities.service.ReminderService;
import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// FE-API-GAPS row 7: "remind me before" on any post with a start time, for its host and the
// people who joined. Joiners of an activity already get 24h and 2h reminders when approved.
// Returns the caller's pending reminders (minutes before) for that post.
@RestController
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ReminderController {

    private final ReminderService reminderService;

    @PostMapping("/posts/{id}/reminder")
    public ResponseEntity<ApiResponse<List<Integer>>> add(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody ReminderRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(reminderService.add(principal.getId(), id, request.minutesBefore())));
    }

    // Without minutesBefore, removes all of the caller's reminders for the post.
    @DeleteMapping("/posts/{id}/reminder")
    public ResponseEntity<ApiResponse<List<Integer>>> remove(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @RequestParam(required = false) Integer minutesBefore) {
        return ResponseEntity.ok(ApiResponse.ok(reminderService.remove(principal.getId(), id, minutesBefore)));
    }
}
