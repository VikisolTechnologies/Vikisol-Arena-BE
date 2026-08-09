package com.vikisol.arena.posts.entity;

// Matches ARENA-V2-PRODUCT-ARCHITECTURE.md §6 verbatim.
public enum PostStatus {
    OPEN, FULL, CLOSED, CANCELLED, EXPIRED;

    public String wireValue() {
        return name().toLowerCase();
    }
}
