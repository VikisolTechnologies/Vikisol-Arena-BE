package com.vikisol.arena.auth.dto;

import jakarta.validation.constraints.NotBlank;

// Mirrors PhoneSigninVerifyRequest - email-OTP is signin-only (no signup counterpart), see
// AuthService.requestEmailSigninOtp's own comment on why.
public record EmailSigninVerifyRequest(
        @NotBlank(message = "is required") String email,
        @NotBlank(message = "is required") String code
) {
}
