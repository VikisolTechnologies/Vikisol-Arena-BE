package com.vikisol.arena.profile.dto;

import java.util.List;

// DPDP data-export self-service action (GET /profile/me/export) - see CandidateProfileService.
public record CandidateDataExport(
        String email,
        CandidateProfileResponse profile,
        List<ApplicationSummary> applications,
        String exportedAt,
        // Architect item 4 (added): the person's entries in every table added for the new app,
        // section by section (see PersonalDataService).
        java.util.Map<String, List<java.util.Map<String, Object>>> arena
) {
    public record ApplicationSummary(String jobTitle, String stage, String appliedAt) {
    }
}
