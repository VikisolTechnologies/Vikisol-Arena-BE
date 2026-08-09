package com.vikisol.arena.profile.dto;

import jakarta.validation.constraints.NotBlank;

// lat/lng are only used for PRECISE (the browser's own current-position report, per an explicit
// Geolocation permission prompt) - immediately geohash-encoded server-side and never persisted
// raw, see DECISIONS.md. city is only used for CITY. Both are ignored for OFF.
public record LocationConsentRequest(
        @NotBlank(message = "is required") String consent,
        Double lat,
        Double lng,
        String city
) {
}
