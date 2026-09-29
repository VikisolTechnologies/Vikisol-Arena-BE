package com.vikisol.arena.interviews.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Row 33 / flow §8: structured feedback per must-have, with no single overall score. `rating`
// is optional now (still 1-5 when sent); `mustHaves` is one entry per must-have:
// { item (≤120), seen: strong | some | none, note? (≤500) }.
public record SubmitInterviewFeedbackRequest(
        @Min(1) @Max(5) Integer rating,
        String strengths,
        String concerns,
        @NotBlank(message = "is required") String recommendation,
        @jakarta.validation.constraints.Size(max = 10, message = "can have at most 10 items") java.util.List<@jakarta.validation.Valid MustHaveFeedback> mustHaves
) {
    public SubmitInterviewFeedbackRequest(Integer rating, String strengths, String concerns, String recommendation) {
        this(rating, strengths, concerns, recommendation, null);
    }

    public record MustHaveFeedback(
            @NotBlank(message = "is required") @jakarta.validation.constraints.Size(max = 120, message = "must be at most 120 characters") String item,
            @NotBlank(message = "is required") @jakarta.validation.constraints.Pattern(regexp = "strong|some|none", message = "must be strong, some or none") String seen,
            @jakarta.validation.constraints.Size(max = 500, message = "must be at most 500 characters") String note
    ) {
    }
}
