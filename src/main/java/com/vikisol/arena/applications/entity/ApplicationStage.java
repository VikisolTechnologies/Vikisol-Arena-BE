package com.vikisol.arena.applications.entity;

// Mirrors arena-web's `ApplicationStage` type exactly.
public enum ApplicationStage {
    APPLIED, SCREENING, INTERVIEW, OFFER, REJECTED;

    public String wireValue() {
        return name().toLowerCase();
    }

    public static ApplicationStage fromWireValue(String value) {
        return ApplicationStage.valueOf(value.trim().toUpperCase());
    }
}
