package com.vikisol.arena.enterprise.entity;

// Mirrors arena-web's EnterpriseProfile.plan type ("free" | "pro" | "enterprise").
public enum Plan {
    FREE, PRO, ENTERPRISE;

    public String wireValue() {
        return name().toLowerCase();
    }

    public static Plan fromWireValue(String value) {
        return Plan.valueOf(value.trim().toUpperCase());
    }
}
