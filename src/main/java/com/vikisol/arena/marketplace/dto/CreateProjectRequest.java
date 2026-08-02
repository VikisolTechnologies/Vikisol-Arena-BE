package com.vikisol.arena.marketplace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateProjectRequest(
        @NotBlank(message = "is required") String title,
        @NotBlank(message = "is required") String description,
        @NotNull(message = "is required") @Positive(message = "must be positive") Integer budgetMin,
        @NotNull(message = "is required") @Positive(message = "must be positive") Integer budgetMax,
        @NotNull(message = "is required") @Positive(message = "must be positive") Integer durationWeeks,
        @NotNull(message = "is required") List<String> skills
) {
}
