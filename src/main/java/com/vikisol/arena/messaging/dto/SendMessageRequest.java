package com.vikisol.arena.messaging.dto;

import jakarta.validation.constraints.NotBlank;

public record SendMessageRequest(@NotBlank(message = "is required") String content) {
}
