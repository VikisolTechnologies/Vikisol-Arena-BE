package com.vikisol.arena.rooms.dto;

import jakarta.validation.constraints.NotBlank;

public record SendRoomMessageRequest(@NotBlank(message = "is required") String content) {
}
