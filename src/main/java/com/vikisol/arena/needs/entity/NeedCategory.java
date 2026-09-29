package com.vikisol.arena.needs.entity;

import java.util.Map;

// What a need (or an offer) is about (G14), per ARENA-APP-FLOW §4 and the frontend's need schema
// ids (src/lib/intake/schemas/need.ts): hyphenated on the wire, e.g. "pet-care". A closed list,
// so Discover can filter on it.
public enum NeedCategory {
    MOVING, TUTORING, REPAIRS, TECH, PET_CARE, PLANT_CARE, ERRANDS, BORROW, RIDES, ADVICE, EVENT_HELP, OTHER;

    // The earlier list's names, still accepted on input.
    private static final Map<String, NeedCategory> ALIASES = Map.of(
            "tech-help", TECH, "pets", PET_CARE, "gardening", PLANT_CARE, "career-advice", ADVICE);

    public String wireValue() {
        return name().toLowerCase().replace('_', '-');
    }

    public static NeedCategory fromWire(String value) {
        String v = value == null ? "" : value.trim().toLowerCase().replace('_', '-');
        for (NeedCategory c : values()) {
            if (c.wireValue().equals(v)) return c;
        }
        if (ALIASES.containsKey(v)) return ALIASES.get(v);
        throw new IllegalArgumentException("category must be one of " + String.join(", ",
                java.util.Arrays.stream(values()).map(NeedCategory::wireValue).toList()));
    }
}
