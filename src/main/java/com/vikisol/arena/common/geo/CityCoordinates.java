package com.vikisol.arena.common.geo;

import java.util.Map;
import java.util.Optional;

/**
 * A small static lookup for {@code CITY}-tier location consent, so it has a real numeric
 * center to rank/filter by without needing an external geocoding API (none is wired - see
 * BLOCKED.md/DECISIONS.md). Deliberately limited to the same city set this codebase already
 * uses everywhere else (IndianData.LOCATIONS) rather than trying to cover arbitrary free-text
 * city names, which would need a real geocoder to do honestly.
 */
public final class CityCoordinates {

    private static final Map<String, double[]> CENTERS = Map.of(
            "Hyderabad", new double[]{17.3850, 78.4867},
            "Bengaluru", new double[]{12.9716, 77.5946},
            "Mumbai", new double[]{19.0760, 72.8777},
            "Pune", new double[]{18.5204, 73.8567},
            "Chennai", new double[]{13.0827, 80.2707}
    );

    private CityCoordinates() {
    }

    public static Optional<double[]> lookup(String city) {
        return Optional.ofNullable(city).map(CENTERS::get);
    }
}
