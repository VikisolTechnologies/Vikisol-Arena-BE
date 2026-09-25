package com.vikisol.arena.posts.entity;

public enum PostJoinOutcome {
    ATTENDED, NO_SHOW;

    public String wireValue() {
        return name().toLowerCase();
    }

    public static PostJoinOutcome fromWire(String value) {
        if (value == null) throw new IllegalArgumentException("Outcome is required");
        return switch (value.trim().toLowerCase()) {
            case "attended" -> ATTENDED;
            case "no_show", "noshow" -> NO_SHOW;
            default -> throw new IllegalArgumentException("Outcome must be attended or no_show");
        };
    }
}
