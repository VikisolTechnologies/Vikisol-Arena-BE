package com.vikisol.arena.jobs.entity;

// Mirrors arena-web's `PostingStatus` type ("open" | "paused" | "closed").
public enum PostingStatus {
    OPEN, PAUSED, CLOSED;

    public String wireValue() {
        return name().toLowerCase();
    }

    public static PostingStatus fromWireValue(String value) {
        return PostingStatus.valueOf(value.trim().toUpperCase());
    }
}
