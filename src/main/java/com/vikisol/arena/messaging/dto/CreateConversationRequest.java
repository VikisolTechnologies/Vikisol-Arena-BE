package com.vikisol.arena.messaging.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateConversationRequest(@NotBlank(message = "is required") String participantUserId, String context) {
}
