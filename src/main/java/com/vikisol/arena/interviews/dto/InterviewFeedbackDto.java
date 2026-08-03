package com.vikisol.arena.interviews.dto;

// Field-for-field mirror of arena-web's `InterviewFeedback` type.
public record InterviewFeedbackDto(
        int rating,
        String strengths,
        String concerns,
        String recommendation,
        String submittedAt
) {
}
