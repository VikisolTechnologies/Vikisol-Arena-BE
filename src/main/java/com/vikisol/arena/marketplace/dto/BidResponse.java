package com.vikisol.arena.marketplace.dto;

// Field-for-field mirror of arena-web's `Bid` type, plus `status` (folded in from what the mock
// tracked separately as MyBidRecord - see BidStatus).
public record BidResponse(
        String id,
        String projectId,
        String bidderName,
        String bidderEmoji,
        int amount,
        int matchPercentage,
        boolean agentPick,
        String submittedAt,
        String status
) {
}
