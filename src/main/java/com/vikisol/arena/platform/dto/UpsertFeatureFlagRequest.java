package com.vikisol.arena.platform.dto;

import jakarta.validation.constraints.NotBlank;

public record UpsertFeatureFlagRequest(
        @NotBlank(message = "is required") String key,
        @NotBlank(message = "is required") String label,
        String description,
        boolean enabled
) {
}
