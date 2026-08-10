package com.vikisol.arena.marketplace.entity;

// ARENA-MASTER-ARCHITECTURE.md PART 7.6: "PROJECT/FREELANCE" share identical composer fields
// (budget, deadline, skills, deliverables) and the same bid -> award -> milestone lifecycle - see
// DECISIONS.md's Step 3 entry. FREELANCE is this lightweight classification on the existing
// Project entity, not a new table: same bidding/milestone/rating machinery either way, just a
// different label and (in the feed/discover facets) a different filter chip.
public enum ProjectKind {
    PROJECT, FREELANCE;

    public String wireValue() {
        return name().toLowerCase();
    }
}
