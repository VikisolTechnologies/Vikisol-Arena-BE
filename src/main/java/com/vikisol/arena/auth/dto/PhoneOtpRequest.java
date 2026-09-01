package com.vikisol.arena.auth.dto;

import jakarta.validation.constraints.NotBlank;

// Shared shape for both "send me a sign-in code" and "send me a signup code" - the phone number
// is all either needs.
public record PhoneOtpRequest(
        @NotBlank(message = "is required") String phoneNumber
) {
}
