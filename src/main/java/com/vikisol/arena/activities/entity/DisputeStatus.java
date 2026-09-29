package com.vikisol.arena.activities.entity;

// NONE: no dispute. OPEN: the participant disputes a no-show; until the host accepts it, the
// no-show never counts against them. ACCEPTED: the host agreed and the outcome is now attended.
public enum DisputeStatus {
    NONE, OPEN, ACCEPTED;

    public String wireValue() {
        return name().toLowerCase();
    }
}
