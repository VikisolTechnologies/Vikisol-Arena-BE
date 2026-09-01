package com.vikisol.arena.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ChangeEmailRequest(
        @NotBlank(message = "is required") @Email(message = "must be a valid email") String newEmail,
        // Same passwordSet=false exception as ChangePasswordRequest - not @NotBlank.
        String currentPassword
) {
}
