package com.vikisol.arena.profile.entity;

// Mirrors arena-web's `AutonomyLevel` type ("manual" | "supervised" | "autopilot").
public enum AutonomyLevel {
    MANUAL, SUPERVISED, AUTOPILOT;

    public String wireValue() {
        return name().toLowerCase();
    }

    public static AutonomyLevel fromWireValue(String value) {
        return AutonomyLevel.valueOf(value.trim().toUpperCase());
    }
}
