package com.vikisol.arena.enterprise.entity;

public enum TenantStatus {
    ACTIVE, SUSPENDED;

    public String wireValue() {
        return name().toLowerCase();
    }
}
