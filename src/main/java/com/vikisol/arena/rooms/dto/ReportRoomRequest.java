package com.vikisol.arena.rooms.dto;

import jakarta.validation.constraints.NotBlank;

public record ReportRoomRequest(@NotBlank(message = "is required") String reason) {
}
