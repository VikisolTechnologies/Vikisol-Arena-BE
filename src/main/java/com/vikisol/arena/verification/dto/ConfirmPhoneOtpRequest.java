package com.vikisol.arena.verification.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmPhoneOtpRequest(@NotBlank(message = "is required") String code) {
}
