package com.vikisol.arena.posts.entity;

// Matches ARENA-V2-PRODUCT-ARCHITECTURE.md §6, plus PAUSED (FE-API-GAPS row 39): the owner hid
// it for now. A paused post is out of feeds and search and takes no new joins or offers; the
// ones it already has are kept.
public enum PostStatus {
    OPEN, FULL, PAUSED, CLOSED, CANCELLED, EXPIRED;

    public String wireValue() {
        return name().toLowerCase();
    }
}
