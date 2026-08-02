package com.vikisol.arena.auth.entity;

// Mirrors arena-web's `Role` type ("talent" | "enterprise") in types.ts.
public enum Role {
    TALENT, ENTERPRISE;

    public String wireValue() {
        return this == TALENT ? "talent" : "enterprise";
    }

    public static Role fromWireValue(String value) {
        if ("talent".equalsIgnoreCase(value)) return TALENT;
        if ("enterprise".equalsIgnoreCase(value)) return ENTERPRISE;
        throw new IllegalArgumentException("Unknown role: " + value);
    }
}
