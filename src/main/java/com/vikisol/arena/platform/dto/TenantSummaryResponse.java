package com.vikisol.arena.platform.dto;

public record TenantSummaryResponse(
        String id,
        String companyName,
        String logoEmoji,
        String plan,
        String status,
        int seatsUsed,
        int seatsTotal,
        int unlockCreditsUsed,
        int unlockCreditsTotal,
        String ownerEmail,
        String createdAt
) {
}
