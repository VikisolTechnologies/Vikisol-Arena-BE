package com.vikisol.arena.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// currentPassword is intentionally NOT @NotBlank - a passwordSet=false account (Google/phone
// signup) has no real current password to supply, and AuthService.changePassword only demands
// it when the account actually has one. Blank/omitted for those accounts is the valid case, not
// a validation failure.
public record ChangePasswordRequest(
        String currentPassword,
        @NotBlank(message = "is required") @Size(min = 6, message = "must be at least 6 characters") String newPassword
) {
}
