package com.vikisol.arena.enterprise.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record CreatePostingRequest(
        @NotBlank(message = "is required") String title,
        @NotBlank(message = "is required") String industry,
        @NotBlank(message = "is required") String location,
        boolean remote,
        @NotBlank(message = "is required") String employmentType,
        @NotNull(message = "is required") @PositiveOrZero Integer salaryMin,
        @NotNull(message = "is required") @PositiveOrZero Integer salaryMax,
        @NotNull(message = "is required") List<String> skills,
        @NotBlank(message = "is required") String description
) {
}
