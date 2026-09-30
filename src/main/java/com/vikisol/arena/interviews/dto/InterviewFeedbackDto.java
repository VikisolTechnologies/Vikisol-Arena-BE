package com.vikisol.arena.interviews.dto;

// Field-for-field mirror of arena-web's `InterviewFeedback` type.
public record InterviewFeedbackDto(
        // Absent when the interviewer gave none (row 33 made it optional).
        Integer rating,
        String strengths,
        String concerns,
        String recommendation,
        String submittedAt,
        // Row 33 (added): per must-have, what the interviewer saw.
        java.util.List<SubmitInterviewFeedbackRequest.MustHaveFeedback> mustHaves
) {
}
