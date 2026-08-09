package com.vikisol.arena.verification.dto;

import jakarta.validation.constraints.NotBlank;

/** ISO-8601 date string (YYYY-MM-DD), self-attested - see DECISIONS.md's age-gating entry. */
public record SetDateOfBirthRequest(@NotBlank(message = "is required") String dateOfBirth) {
}
