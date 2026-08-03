package com.vikisol.arena.interviews.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignHiringManagerRequest(@NotBlank(message = "is required") String hiringManagerUserId) {
}
