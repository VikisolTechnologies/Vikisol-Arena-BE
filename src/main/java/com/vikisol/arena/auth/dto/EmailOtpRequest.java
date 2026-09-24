package com.vikisol.arena.auth.dto;

import jakarta.validation.constraints.NotBlank;

// Mirrors PhoneOtpRequest - "send me a sign-in code" for the email-OTP flow.
public record EmailOtpRequest(
        @NotBlank(message = "is required") String email
) {
}
