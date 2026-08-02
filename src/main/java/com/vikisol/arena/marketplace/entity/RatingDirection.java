package com.vikisol.arena.marketplace.entity;

// New concept - AUDIT.md item (b): "no Rating/Review type anywhere in the contract" despite
// Phase 3 requiring a full two-way rating at project completion. Direction records who rated whom.
public enum RatingDirection {
    CLIENT_TO_TALENT, TALENT_TO_CLIENT
}
