package com.vikisol.arena.follows.entity;

public enum FollowTargetType {
    USER, COMPANY;

    public String wireValue() {
        return name().toLowerCase();
    }
}
