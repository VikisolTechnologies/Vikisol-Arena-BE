package com.vikisol.arena.profile.dto;

import java.util.List;

// DPDP data-export self-service action (GET /profile/me/export) - see CandidateProfileService.
public record CandidateDataExport(
        String email,
        CandidateProfileResponse profile,
        List<ApplicationSummary> applications,
        String exportedAt
) {
    public record ApplicationSummary(String jobTitle, String stage, String appliedAt) {
    }
}
