package com.vikisol.arena.platform.entity;

public enum ModerationStatus {
    PENDING, DISMISSED, TAKEN_DOWN;

    public String wireValue() {
        return name().toLowerCase();
    }
}
