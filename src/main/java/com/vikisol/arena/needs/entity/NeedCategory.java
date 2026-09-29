package com.vikisol.arena.needs.entity;

// What a need (or an offer) is about (G14). A closed list, so Discover can filter on it.
public enum NeedCategory {
    MOVING, REPAIRS, TECH_HELP, TUTORING, ERRANDS, PETS, RIDES, COOKING, CLEANING, GARDENING,
    CAREER_ADVICE, CREATIVE, OTHER;

    public String wireValue() {
        return name().toLowerCase();
    }

    public static NeedCategory fromWire(String value) {
        for (NeedCategory c : values()) {
            if (c.wireValue().equals(value == null ? "" : value.trim().toLowerCase())) return c;
        }
        throw new IllegalArgumentException("category must be one of " + String.join(", ",
                java.util.Arrays.stream(values()).map(NeedCategory::wireValue).toList()));
    }
}
