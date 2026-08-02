package com.vikisol.arena.jobs.entity;

// Mirrors arena-web's `EmploymentType` type ("Full Time" | "Contract" | "Internship").
public enum EmploymentType {
    FULL_TIME, CONTRACT, INTERNSHIP;

    public String wireValue() {
        return switch (this) {
            case FULL_TIME -> "Full Time";
            case CONTRACT -> "Contract";
            case INTERNSHIP -> "Internship";
        };
    }

    public static EmploymentType fromWireValue(String value) {
        return switch (value) {
            case "Full Time" -> FULL_TIME;
            case "Contract" -> CONTRACT;
            case "Internship" -> INTERNSHIP;
            default -> throw new IllegalArgumentException("Unknown employment type: " + value);
        };
    }
}
