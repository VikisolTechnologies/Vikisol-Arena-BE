package com.vikisol.arena.posts.entity;

// LOCAL exists for schema-completeness with ARENA-V2-PRODUCT-ARCHITECTURE.md's §6 data model
// (avoids a second migration when Phase B adds geo) but the Phase A composer never offers it -
// there's no location capture to rank by yet. Only GLOBAL/FOLLOWERS are functional this phase.
public enum PostAudience {
    GLOBAL, FOLLOWERS, LOCAL;

    public String wireValue() {
        return name().toLowerCase();
    }
}
