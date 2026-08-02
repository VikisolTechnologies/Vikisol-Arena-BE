package com.vikisol.arena.applications.dto;

// Field-for-field mirror of arena-web's `Application` type (the candidate-side view).
public record ApplicationResponse(
        String id,
        String jobId,
        String stage,
        String appliedAt,
        String updatedAt
) {
}
