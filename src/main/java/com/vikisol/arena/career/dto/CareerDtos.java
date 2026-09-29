package com.vikisol.arena.career.dto;

import java.util.List;

// Bodies for /career (G18-G21).
public final class CareerDtos {

    private CareerDtos() {
    }

    // PUT /career/me. First call needs intent; later calls change only the fields sent.
    public record SetupRequest(
            String intent,
            String desiredRole,
            String experienceLevel,
            String workMode,
            List<String> preferredLocations,
            String noticePeriod,
            String compensationVisibility,
            Integer expectedMin,
            Integer expectedMax,
            // Flow §6 extras (row 19), all optional. Only the fields sent change; an empty list or
            // "" clears one.
            String currentCompany,
            String status,
            String lastWorkingDay,
            Integer experienceMonths,
            String roleFamily,
            List<SkillEntry> skills,
            List<String> sapModules,
            List<String> certifications,
            CurrentCtc currentCtc,
            ExpectedCtc expectedCtc,
            Boolean negotiable,
            List<String> desiredRoles,
            List<String> workModes,
            Boolean relocate,
            String shift,
            List<String> companySizes,
            List<String> links,
            Education education,
            List<String> languages,
            java.util.Map<String, String> visibility
    ) {
        public SetupRequest(String intent, String desiredRole, String experienceLevel, String workMode, List<String> preferredLocations,
                            String noticePeriod, String compensationVisibility, Integer expectedMin, Integer expectedMax) {
            this(intent, desiredRole, experienceLevel, workMode, preferredLocations, noticePeriod, compensationVisibility, expectedMin,
                    expectedMax, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null);
        }
    }

    // proficiency: learning | working | strong | expert.
    public record SkillEntry(String name, String proficiency, Integer years) {
    }

    // Yearly INR.
    public record CurrentCtc(Integer fixed, Integer variable) {
    }

    public record ExpectedCtc(Integer min, Integer max) {
    }

    public record Education(String degree, String institution, Integer year) {
    }

    // The flow §6 extras as someone sees them. Hidden fields are absent. currentCtc and
    // expectedCtc appear only for the owner, or for an employer the person applied to with
    // "include my CTC" ticked.
    public record CareerDetails(
            String currentCompany,
            String status,
            String lastWorkingDay,
            Integer experienceMonths,
            String roleFamily,
            List<SkillEntry> skills,
            List<String> sapModules,
            List<String> certifications,
            CurrentCtc currentCtc,
            ExpectedCtc expectedCtc,
            Boolean negotiable,
            List<String> desiredRoles,
            List<String> workModes,
            Boolean relocate,
            String shift,
            List<String> companySizes,
            List<String> links,
            Education education,
            List<String> languages
    ) {
    }

    public record PublishRequest(Boolean openToWork) {
    }

    // The owner's own view: everything, including pay and whether it's published.
    public record CareerSelfView(
            String intent,
            String desiredRole,
            String experienceLevel,
            String workMode,
            List<String> preferredLocations,
            String noticePeriod,
            String compensationVisibility,
            Integer expectedMin,
            Integer expectedMax,
            String currency,
            boolean openToWork,
            boolean published,
            String publishedAt,
            // Row 19 (added): every extra field, and each field's visibility (defaults filled in).
            CareerDetails details,
            java.util.Map<String, String> visibility
    ) {
    }

    // What one audience sees. Hidden fields are absent (null), never faked.
    public record CareerPublicView(
            String userId,
            String audience,
            String desiredRole,
            String experienceLevel,
            String workMode,
            List<String> preferredLocations,
            String noticePeriod,
            Boolean openToWork,
            List<String> skills,
            Integer expectedMin,
            Integer expectedMax,
            String currency,
            // Row 19 (added): the extra fields this audience may see.
            CareerDetails details
    ) {
    }

    // G20 privacy preview: exactly what each audience would see once published.
    public record PrivacyPreview(
            boolean published,
            String compensationShownTo,
            CareerPublicView employers,
            CareerPublicView connections,
            CareerPublicView neighbors,
            // Added: what an employer you apply to sees (without "include my CTC").
            CareerPublicView employersYouApplyTo
    ) {
    }
}
