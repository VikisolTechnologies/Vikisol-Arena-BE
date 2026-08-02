package com.vikisol.arena.marketplace.dto;

import java.util.List;

// Field-for-field mirror of arena-web's `Project` type, plus the optional `mine`/`awardedBidId`/
// `milestones` fields that mock's separate MyProject (myProjects.ts) type added - included here
// always (null/empty when not applicable) rather than as a second parallel DTO.
public record ProjectResponse(
        String id,
        String title,
        String description,
        int budgetMin,
        int budgetMax,
        int durationWeeks,
        List<String> skills,
        String postedBy,
        String status,
        String endsAt,
        List<BidResponse> bids,
        Boolean mine,
        String awardedBidId,
        List<MilestoneResponse> milestones
) {
}
