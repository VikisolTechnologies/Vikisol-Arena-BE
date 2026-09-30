package com.vikisol.arena.activities.entity;

public enum WaitlistStatus {
    WAITING, PROMOTED, LEFT, SKIPPED;

    public String wireValue() {
        return name().toLowerCase();
    }
}
