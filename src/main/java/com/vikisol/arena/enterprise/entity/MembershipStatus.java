package com.vikisol.arena.enterprise.entity;

public enum MembershipStatus {
    INVITED, ACTIVE, SUSPENDED;

    public String wireValue() {
        return name().toLowerCase();
    }
}
