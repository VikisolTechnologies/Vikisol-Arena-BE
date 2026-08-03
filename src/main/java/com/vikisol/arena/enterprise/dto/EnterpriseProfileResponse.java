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
        String status
) {
}
