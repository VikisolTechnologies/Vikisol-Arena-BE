package com.vikisol.arena.posts.entity;

// ARENA-V2-PRODUCT-ARCHITECTURE.md Phase A: only intent types with no pre-existing backing
// entity live here. JOB/PROJECT stay on their own existing tables (JobPosting/Project) -
// unifying those into `posts` is explicitly later-phase work, not a migration of live
// production data. COMPANY was added in the post-spec reconciliation pass (Phase C): §3.5/§6
// name it explicitly ("Company posts appear in the feed... gives enterprises a reason to be
// here between hires") and it has no pre-existing backing entity either, same as
// ACTIVITY/ASK/UPDATE - see DECISIONS.md and PostService.createCompanyPost.
public enum PostIntentType {
    ACTIVITY, ASK, UPDATE, COMPANY, OFFER;

    public String wireValue() {
        return name().toLowerCase();
    }

    public boolean isDiscussion() {
        return this == ASK || this == UPDATE || this == OFFER;
    }
}
