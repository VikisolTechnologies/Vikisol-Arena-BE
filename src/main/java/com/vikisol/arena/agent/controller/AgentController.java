package com.vikisol.arena.agent.controller;

import com.vikisol.arena.agent.dto.AgentConversationResponse;
import com.vikisol.arena.agent.dto.AgentMessageResponse;
import com.vikisol.arena.agent.dto.SendAgentMessageRequest;
import com.vikisol.arena.agent.service.AgentService;
import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @GetMapping("/conversation")
    public ResponseEntity<ApiResponse<AgentConversationResponse>> getOrCreateConversation(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(agentService.getOrCreateActiveConversation(principal.getId())));
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<ApiResponse<List<AgentMessageResponse>>> getMessages(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(agentService.getMessages(principal.getId(), id)));
    }

    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<ApiResponse<AgentMessageResponse>> sendMessage(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody SendAgentMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(agentService.sendMessage(principal.getId(), id, request.content())));
    }
    public record ActionDecisionRequest(@jakarta.validation.constraints.NotNull Boolean approve) {}

    @PostMapping("/actions/{id}")
    public ResponseEntity<ApiResponse<com.vikisol.arena.agent.dto.AgentActionResponse>> decideAction(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody ActionDecisionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(agentService.decideAction(principal.getId(), id, request.approve())));
    }

}
