package com.vikisol.arena.profile.entity;

// Mirrors arena-web's `Industry` type exactly.
public enum Industry {
    ENGINEERING, DESIGN, SALES, HEALTHCARE, LOGISTICS;

    public String wireValue() {
        return switch (this) {
            case ENGINEERING -> "Engineering";
            case DESIGN -> "Design";
            case SALES -> "Sales";
            case HEALTHCARE -> "Healthcare";
            case LOGISTICS -> "Logistics";
        };
    }

    public static Industry fromWireValue(String value) {
        for (Industry i : values()) {
            if (i.wireValue().equalsIgnoreCase(value) || i.name().equalsIgnoreCase(value)) return i;
        }
        throw new IllegalArgumentException("Unknown industry: " + value);
    }
}
