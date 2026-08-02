package com.vikisol.arena.profile.entity;

// Mirrors arena-web's `OpenTo` type ("full-time" | "contract" | "projects").
public enum OpenTo {
    FULL_TIME, CONTRACT, PROJECTS;

    public String wireValue() {
        return switch (this) {
            case FULL_TIME -> "full-time";
            case CONTRACT -> "contract";
            case PROJECTS -> "projects";
        };
    }

    public static OpenTo fromWireValue(String value) {
        return switch (value) {
            case "full-time" -> FULL_TIME;
            case "contract" -> CONTRACT;
            case "projects" -> PROJECTS;
            default -> throw new IllegalArgumentException("Unknown openTo value: " + value);
        };
    }
}
