package com.vikisol.arena.applications.dto;

import jakarta.validation.constraints.NotBlank;

public record AdvanceStageRequest(@NotBlank(message = "is required") String stage) {
}
