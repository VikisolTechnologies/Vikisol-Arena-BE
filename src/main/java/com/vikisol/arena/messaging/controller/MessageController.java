package com.vikisol.arena.messaging.controller;

import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.messaging.dto.ConversationResponse;
import com.vikisol.arena.messaging.dto.CreateConversationRequest;
import com.vikisol.arena.messaging.dto.SendMessageRequest;
import com.vikisol.arena.messaging.dto.ThreadMessageResponse;
import com.vikisol.arena.messaging.service.ConversationService;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageController {

    private final ConversationService conversationService;

    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<List<ConversationResponse>>> getConversations(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(conversationService.getMyConversations(principal.getId())));
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<ApiResponse<List<ThreadMessageResponse>>> getMessages(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(conversationService.getMessages(principal.getId(), id)));
    }

    @PostMapping("/conversations")
    public ResponseEntity<ApiResponse<ConversationResponse>> getOrCreateConversation(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody CreateConversationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                conversationService.getOrCreate(principal.getId(), UUID.fromString(request.participantUserId()), request.context())));
    }

    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<ApiResponse<ThreadMessageResponse>> sendMessage(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id, @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(conversationService.sendMessage(principal.getId(), id, request.content())));
    }
}
