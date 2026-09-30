package com.vikisol.arena.profile.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

// FE-API-GAPS 4-5: PATCH /profile/me. Every field is optional; only the ones sent change.
public record PatchProfileRequest(
        @Size(min = 1, max = 80, message = "must be 1 to 80 characters") String name,
        @Size(max = 100, message = "must be at most 100 characters") String title,
        @Size(max = 160, message = "must be at most 160 characters") String bio,
        List<String> availability
) {
}
