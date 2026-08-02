package com.vikisol.arena.activity.entity;

// Mirrors arena-web's `ActivityEventType` type exactly.
public enum ActivityEventType {
    SCANNED, APPLIED, MATCH_FOUND, INTERVIEW_PROPOSED, INTERVIEW_CONFIRMED, MESSAGE;

    public String wireValue() {
        return switch (this) {
            case SCANNED -> "scanned";
            case APPLIED -> "applied";
            case MATCH_FOUND -> "match_found";
            case INTERVIEW_PROPOSED -> "interview_proposed";
            case INTERVIEW_CONFIRMED -> "interview_confirmed";
            case MESSAGE -> "message";
        };
    }
}
