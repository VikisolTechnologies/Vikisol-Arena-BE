package com.vikisol.arena.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInvitationRequest(
        @NotBlank(message = "is required") String token,
        @NotBlank(message = "is required") String name,
        @NotBlank(message = "is required") @Size(min = 8, message = "must be at least 8 characters") String password
) {
}
