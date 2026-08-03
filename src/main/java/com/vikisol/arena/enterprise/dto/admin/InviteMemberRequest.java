package com.vikisol.arena.enterprise.dto.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InviteMemberRequest(
        @NotBlank(message = "is required") @Email(message = "must be a valid email") String email,
        @NotBlank(message = "is required") String role
) {
}
