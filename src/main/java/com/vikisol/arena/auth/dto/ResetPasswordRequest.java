package com.vikisol.arena.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "is required") @Email(message = "must be a valid email") String email,
        @NotBlank(message = "is required") String token,
        @NotBlank(message = "is required") @Size(min = 6, message = "must be at least 6 characters") String newPassword
) {
}
