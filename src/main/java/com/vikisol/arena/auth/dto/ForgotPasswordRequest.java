package com.vikisol.arena.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank(message = "is required") @Email(message = "must be a valid email") String email
) {
}
