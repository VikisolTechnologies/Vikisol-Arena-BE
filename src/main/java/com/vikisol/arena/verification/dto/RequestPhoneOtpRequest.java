package com.vikisol.arena.verification.dto;

import jakarta.validation.constraints.NotBlank;

public record RequestPhoneOtpRequest(@NotBlank(message = "is required") String phoneNumber) {
}
