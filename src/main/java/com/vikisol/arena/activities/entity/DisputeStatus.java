package com.vikisol.arena.activities.entity;

// NONE: no dispute. OPEN: the participant disputes a no-show; until the host accepts it, the
// no-show never counts against them. ACCEPTED: the host agreed and the outcome is now attended.
public enum DisputeStatus {
    // REJECTED (V37): an Arena admin reviewed the dispute and kept the no-show, which is then final.
    NONE, OPEN, ACCEPTED, REJECTED;

    public String wireValue() {
        return name().toLowerCase();
    }
}
