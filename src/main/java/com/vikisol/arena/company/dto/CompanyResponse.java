package com.vikisol.arena.company.dto;

// ARENA-V2-PRODUCT-ARCHITECTURE.md Phase C - the talent-facing view of an EnterpriseProfile.
// Deliberately excludes seatsUsed/seatsTotal/unlockCreditsUsed/unlockCreditsTotal/plan/status -
// those are the tenant's own internal business data, not public company info (see DECISIONS.md's
// "same redaction pattern as TalentSearchService, opposite direction" note).
public record CompanyResponse(
        String id,
        String name,
        String emoji,
        String industry,
        String size,
        int openJobCount,
        long followerCount,
        Boolean viewerFollows
) {
}
