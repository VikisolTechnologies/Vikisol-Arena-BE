package com.vikisol.arena.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// ARCHITECT-REVIEW-BE-1 (architect notes on B8/B9): the 18+ rule (AgeUtil.MINIMUM_AGE) was only
// ever checked at activity create/join, never at signup itself - a minor could otherwise use
// every other part of Arena freely. dateOfBirth is now required here too, ISO-8601 (YYYY-MM-DD),
// self-attested same as PUT /verification/date-of-birth.
public record SignUpRequest(
        @NotBlank(message = "is required") String name,
        @NotBlank(message = "is required") @Email(message = "must be a valid email") String email,
        @NotBlank(message = "is required") @Size(min = 8, message = "must be at least 8 characters") String password,
        @NotBlank(message = "is required") String role,
        @NotBlank(message = "is required") String dateOfBirth
) {
}
