package com.vikisol.arena.enterprise.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateEnterpriseProfileRequest(
        @NotBlank(message = "is required") String companyName,
        @NotBlank(message = "is required") String logoEmoji,
        @NotBlank(message = "is required") String industry,
        @NotBlank(message = "is required") String size,
        @NotNull(message = "is required") List<String> hiringFor,
        // Row 29 (optional extras): "" clears one. The logo is uploaded separately
        // (POST /enterprise/profile/me/logo).
        @jakarta.validation.constraints.Size(max = 255, message = "must be at most 255 characters") String website,
        String gstin,
        String cin,
        @jakarta.validation.constraints.Size(max = 60, message = "must be at most 60 characters") String hqCity
) {
    public UpdateEnterpriseProfileRequest(String companyName, String logoEmoji, String industry, String size, List<String> hiringFor) {
        this(companyName, logoEmoji, industry, size, hiringFor, null, null, null, null);
    }
}
