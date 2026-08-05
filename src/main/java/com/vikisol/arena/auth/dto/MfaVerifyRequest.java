package com.vikisol.arena.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaVerifyRequest(
        @NotBlank(message = "is required") String pendingToken,
        @NotBlank(message = "is required") String code
) {
}
