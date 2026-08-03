package com.vikisol.arena.interviews.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubmitInterviewFeedbackRequest(
        @NotNull(message = "is required") @Min(1) @Max(5) Integer rating,
        String strengths,
        String concerns,
        @NotBlank(message = "is required") String recommendation
) {
}
