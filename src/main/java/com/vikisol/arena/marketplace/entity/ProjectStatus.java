package com.vikisol.arena.marketplace.entity;

// Mirrors arena-web's `Project["status"]` type ("open" | "awarded" | "closed").
public enum ProjectStatus {
    OPEN, AWARDED, CLOSED;

    public String wireValue() {
        return name().toLowerCase();
    }
}
