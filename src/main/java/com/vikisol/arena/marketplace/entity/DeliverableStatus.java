package com.vikisol.arena.marketplace.entity;

public enum DeliverableStatus {
    SUBMITTED, ACCEPTED, REJECTED;

    public String wireValue() {
        return name().toLowerCase();
    }
}
