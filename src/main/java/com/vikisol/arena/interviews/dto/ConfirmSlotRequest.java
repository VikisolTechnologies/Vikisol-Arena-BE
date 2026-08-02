package com.vikisol.arena.interviews.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmSlotRequest(@NotBlank(message = "is required") String slotId) {
}
