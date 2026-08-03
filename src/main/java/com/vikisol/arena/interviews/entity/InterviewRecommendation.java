package com.vikisol.arena.interviews.entity;

// Mirrors arena-web's `InterviewRecommendation` type ("advance" | "hold" | "reject").
public enum InterviewRecommendation {
    ADVANCE, HOLD, REJECT;

    public String wireValue() {
        return name().toLowerCase();
    }

    public static InterviewRecommendation fromWireValue(String value) {
        return InterviewRecommendation.valueOf(value.trim().toUpperCase());
    }
}
