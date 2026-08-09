package com.vikisol.arena.verification.dto;

public record VerificationStatusResponse(
        String verificationLevel,
        boolean phoneVerified,
        String phoneNumber,
        boolean otpPending
) {
}
