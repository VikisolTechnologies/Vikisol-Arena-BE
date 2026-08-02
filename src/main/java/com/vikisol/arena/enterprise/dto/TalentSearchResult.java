package com.vikisol.arena.enterprise.dto;

import com.vikisol.arena.profile.dto.CandidateProfileResponse;

// Field-for-field mirror of arena-web's `EnterpriseSearchResult` type.
public record TalentSearchResult(
        CandidateProfileResponse candidate,
        int matchPercentage,
        String fitBlurb,
        String availability,
        boolean unlocked
) {
}
