package com.vikisol.arena.posts.entity;

// Drives the join/approve flow: PUBLIC auto-approves a join request on submit, APPROVAL leaves
// it PENDING for the author to decide. Only meaningful for ACTIVITY/ASK posts - UPDATE posts
// never accept joins at all, so this field is ignored for them.
public enum PostVisibility {
    PUBLIC, APPROVAL;

    public String wireValue() {
        return name().toLowerCase();
    }
}
