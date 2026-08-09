package com.vikisol.arena.profile.dto;

import java.util.List;

// ARENA-V2-PRODUCT-ARCHITECTURE.md Phase C profile revamp - the other-viewer-facing view of a
// CandidateProfile, today's `/profile/me` (CandidateProfileResponse) is self-only. Redacted the
// same direction as company pages: no cvUrl/cvFileName (resume download stays owner-only), no
// rateFloor/consent/autonomy (not another candidate's business), no approxLat/approxLng (only
// homeCity, and only when the profile's own locationConsent isn't OFF).
public record PublicCandidateProfileResponse(
        String id,
        String name,
        String avatarEmoji,
        String title,
        String industry,
        String location,
        boolean remote,
        List<SkillDto> skills,
        int experienceYears,
        List<String> openTo,
        int careerHealth,
        String bio,
        String verificationLevel,
        boolean phoneVerified,
        String homeCity,
        long followerCount,
        long followingCount,
        Boolean viewerFollows
) {
}
