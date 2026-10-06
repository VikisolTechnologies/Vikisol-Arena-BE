package com.vikisol.arena.applications.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// message (FE-API-GAPS row 31, optional): the company's personal note to the candidate, sent with
// the stage change. "rejected" without one sends Arena's kind standard message.
public record AdvanceStageRequest(
        @NotBlank(message = "is required") String stage,
        @Size(max = 600, message = "must be at most 600 characters") String message
) {
}
