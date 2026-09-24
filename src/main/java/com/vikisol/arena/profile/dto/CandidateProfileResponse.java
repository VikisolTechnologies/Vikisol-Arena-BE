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
        String cvFileName,
        String locationConsent,
        String homeCity,
        Double approxLat,
        Double approxLng,
        Boolean cameForJob,
        String organization,
        Integer currentCtc,
        Integer expectedCtc,
        String preferredLocation,
        // True for every self/public view (CandidateProfileMapper.toResponse callers always have
        // full access to what they're looking at). Only ever false when TalentSearchService's
        // redactIfLocked() overrides it for an enterprise viewer who hasn't unlocked this
        // candidate - lets the frontend read unlock state directly off the response instead of
        // inferring it from whether cvUrl happens to be null (ambiguous: a candidate with no CV
        // uploaded at all also has a null cvUrl, unlock status or not).
        boolean fullAccess
) {
}
