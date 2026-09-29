package com.vikisol.arena.enterprise.dto;

import java.util.List;

// Field-for-field mirror of arena-web's `EnterpriseProfile` type.
public record EnterpriseProfileResponse(
        String companyName,
        String logoEmoji,
        String industry,
        String size,
        List<String> hiringFor,
        String plan,
        int seatsUsed,
        int seatsTotal,
        int unlockCreditsUsed,
        int unlockCreditsTotal,
        String status,
        // Row 29 (added): the workspace details, and verification: none | pending | verified |
        // rejected, with the Arena admin's reason on a rejection.
        String website,
        String gstin,
        String cin,
        String hqCity,
        String logoUrl,
        String verification,
        String verificationNote
) {
}
