package com.vikisol.arena.needs.entity;

public enum ResponseStatus {
    PENDING, ACCEPTED, DECLINED, WITHDRAWN;

    public String wireValue() {
        return name().toLowerCase();
    }
}
