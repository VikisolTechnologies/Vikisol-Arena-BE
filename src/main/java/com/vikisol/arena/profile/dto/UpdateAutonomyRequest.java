package com.vikisol.arena.profile.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateAutonomyRequest(@NotBlank(message = "is required") String autonomy) {
}
