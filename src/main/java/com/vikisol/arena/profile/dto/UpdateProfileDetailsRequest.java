package com.vikisol.arena.profile.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateProfileDetailsRequest(
        @NotBlank(message = "is required") String name,
        @NotBlank(message = "is required") String title,
        @NotBlank(message = "is required") String industry,
        @Min(value = 0, message = "must be 0 or more") int experienceYears,
        @Min(value = 0, message = "must be 0 or more") int rateFloor,
        @NotNull(message = "is required") List<String> openTo) {
}
