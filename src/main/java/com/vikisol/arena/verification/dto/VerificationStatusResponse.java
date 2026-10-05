package com.vikisol.arena.verification.dto;

// API-ISSUES.md: dateOfBirthSet lets the FE skip re-asking the age gate on a second device once
// it's already on file, instead of always showing AgeGateStep regardless.
public record VerificationStatusResponse(
        String verificationLevel,
        boolean phoneVerified,
        String phoneNumber,
        boolean otpPending,
        boolean dateOfBirthSet
) {
}
