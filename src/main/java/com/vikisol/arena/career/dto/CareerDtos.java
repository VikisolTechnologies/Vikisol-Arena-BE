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
            Integer expectedMax
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
            String publishedAt
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
            String currency
    ) {
    }

    // G20 privacy preview: exactly what each audience would see once published.
    public record PrivacyPreview(
            boolean published,
            String compensationShownTo,
            CareerPublicView employers,
            CareerPublicView connections,
            CareerPublicView neighbors
    ) {
    }
}
