package com.vikisol.arena.rooms.controller;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.rooms.dto.ReportRoomRequest;
import com.vikisol.arena.rooms.dto.RoomMemberResponse;
import com.vikisol.arena.rooms.dto.RoomMessageResponse;
import com.vikisol.arena.rooms.dto.RoomResponse;
import com.vikisol.arena.rooms.dto.SendRoomMessageRequest;
import com.vikisol.arena.rooms.service.RoomService;
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
@RequestMapping("/rooms")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TALENT')")
public class RoomController {

    private final RoomService roomService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoomResponse>>> getMyRooms(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(roomService.getMyRooms(principal.getId())));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<ApiResponse<List<RoomMessageResponse>>> getMessages(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(roomService.getMessages(principal.getId(), id)));
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<ApiResponse<List<RoomMemberResponse>>> getMembers(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(roomService.getMembers(principal.getId(), id)));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<ApiResponse<RoomMessageResponse>> sendMessage(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody SendRoomMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(roomService.sendMessage(principal.getId(), id, request.content())));
    }

    @PutMapping("/{id}/mute")
    public ResponseEntity<ApiResponse<Void>> mute(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        roomService.setMuted(principal.getId(), id, true);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PutMapping("/{id}/unmute")
    public ResponseEntity<ApiResponse<Void>> unmute(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        roomService.setMuted(principal.getId(), id, false);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        roomService.markRead(principal.getId(), id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/{id}/report")
    public ResponseEntity<ApiResponse<Void>> report(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody ReportRoomRequest request) {
        roomService.report(principal.getId(), id, request.reason());
        return ResponseEntity.ok(ApiResponse.ok("Report submitted", null));
    }
}
