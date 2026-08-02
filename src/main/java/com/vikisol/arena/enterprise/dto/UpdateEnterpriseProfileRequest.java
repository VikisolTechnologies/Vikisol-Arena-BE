package com.vikisol.arena.enterprise.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateEnterpriseProfileRequest(
        @NotBlank(message = "is required") String companyName,
        @NotBlank(message = "is required") String logoEmoji,
        @NotBlank(message = "is required") String industry,
        @NotBlank(message = "is required") String size,
        @NotNull(message = "is required") List<String> hiringFor
) {
}
