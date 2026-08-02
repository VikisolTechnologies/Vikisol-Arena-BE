package com.vikisol.arena.marketplace.entity;

// New concept - AUDIT.md item (b): mock had no Milestone/deliverable data model at all, only
// UI-only checkbox state. This is the real lifecycle Phase 3 needs: pending work item through to
// an accepted deliverable.
public enum MilestoneStatus {
    PENDING, IN_PROGRESS, SUBMITTED, ACCEPTED, REJECTED;

    public String wireValue() {
        return name().toLowerCase();
    }
}
