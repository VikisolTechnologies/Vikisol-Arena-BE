package com.vikisol.arena.enterprise.entity;

public enum InvitationStatus {
    PENDING, ACCEPTED, EXPIRED, REVOKED;

    public String wireValue() {
        return name().toLowerCase();
    }
}
