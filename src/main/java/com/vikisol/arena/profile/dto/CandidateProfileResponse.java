package com.vikisol.arena.profile.dto;

import java.util.List;

// Field-for-field mirror of arena-web's `CandidateProfile` type in types.ts.
public record CandidateProfileResponse(
        String id,
        String name,
        String avatarEmoji,
        String title,
        String industry,
        String location,
        boolean remote,
        List<SkillDto> skills,
        int experienceYears,
        int rateFloor,
        List<String> openTo,
        int careerHealth,
        ConsentDto consent,
        String autonomy,
        String bio,
        String cvUrl,
        String cvFileName
) {
}
