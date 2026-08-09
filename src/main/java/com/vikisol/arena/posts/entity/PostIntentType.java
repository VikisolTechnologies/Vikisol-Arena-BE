package com.vikisol.arena.posts.entity;

// ARENA-V2-PRODUCT-ARCHITECTURE.md Phase A: only the three intent types with no pre-existing
// backing entity. JOB/PROJECT/COMPANY stay on their own existing tables (JobPosting/Project;
// company pages don't exist yet) - unifying those into `posts` is explicitly later-phase work,
// not a Phase A migration of live production data.
public enum PostIntentType {
    ACTIVITY, ASK, UPDATE;

    public String wireValue() {
        return name().toLowerCase();
    }
}
