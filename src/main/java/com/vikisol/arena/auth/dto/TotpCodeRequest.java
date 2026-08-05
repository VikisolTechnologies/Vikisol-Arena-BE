package com.vikisol.arena.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TotpCodeRequest(
        @NotBlank(message = "is required") String code
) {
}
