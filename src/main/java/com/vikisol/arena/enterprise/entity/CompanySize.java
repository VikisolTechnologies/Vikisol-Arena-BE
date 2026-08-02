package com.vikisol.arena.enterprise.entity;

// Mirrors arena-web's `CompanySize` type ("1-10" | "11-50" | "51-200" | "201-1000" | "1000+").
public enum CompanySize {
    S_1_10, S_11_50, S_51_200, S_201_1000, S_1000_PLUS;

    public String wireValue() {
        return switch (this) {
            case S_1_10 -> "1-10";
            case S_11_50 -> "11-50";
            case S_51_200 -> "51-200";
            case S_201_1000 -> "201-1000";
            case S_1000_PLUS -> "1000+";
        };
    }

    public static CompanySize fromWireValue(String value) {
        return switch (value) {
            case "1-10" -> S_1_10;
            case "11-50" -> S_11_50;
            case "51-200" -> S_51_200;
            case "201-1000" -> S_201_1000;
            case "1000+" -> S_1000_PLUS;
            default -> throw new IllegalArgumentException("Unknown company size: " + value);
        };
    }
}
