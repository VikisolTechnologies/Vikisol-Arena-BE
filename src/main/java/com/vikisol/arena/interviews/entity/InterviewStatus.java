package com.vikisol.arena.interviews.entity;

// Mirrors arena-web's Interview["status"] type ("proposed" | "confirmed" | "completed" | "cancelled").
public enum InterviewStatus {
    PROPOSED, CONFIRMED, COMPLETED, CANCELLED;

    public String wireValue() {
        return name().toLowerCase();
    }
}
