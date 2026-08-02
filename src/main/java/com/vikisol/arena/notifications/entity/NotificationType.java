package com.vikisol.arena.notifications.entity;

// Mirrors arena-web's `NotificationType` type ("agent" | "interview" | "bid" | "system").
public enum NotificationType {
    AGENT, INTERVIEW, BID, SYSTEM;

    public String wireValue() {
        return name().toLowerCase();
    }
}
