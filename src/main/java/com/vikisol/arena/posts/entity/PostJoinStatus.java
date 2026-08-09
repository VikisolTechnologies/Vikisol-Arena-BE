package com.vikisol.arena.posts.entity;

public enum PostJoinStatus {
    PENDING, APPROVED, DECLINED;

    public String wireValue() {
        return name().toLowerCase();
    }
}
