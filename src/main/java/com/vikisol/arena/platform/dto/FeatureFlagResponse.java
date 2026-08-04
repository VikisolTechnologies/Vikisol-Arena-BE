package com.vikisol.arena.platform.dto;

public record FeatureFlagResponse(
        String id,
        String key,
        String label,
        String description,
        boolean enabled
) {
}
