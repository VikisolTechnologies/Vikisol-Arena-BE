package com.vikisol.arena.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record PhoneSigninVerifyRequest(
        @NotBlank(message = "is required") String phoneNumber,
        @NotBlank(message = "is required") String code
) {
}
