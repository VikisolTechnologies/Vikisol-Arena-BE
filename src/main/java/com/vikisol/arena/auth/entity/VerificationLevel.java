package com.vikisol.arena.auth.entity;

// ARENA-V2-PRODUCT-ARCHITECTURE.md §4: "creators can require a verification level to join."
// PHONE is fully functional this pass (real OTP flow, see verification package). ID exists in
// the enum and every gate already checks `>=` a required level generically, but has no real or
// manual-review path to reach it yet - see BLOCKED.md.
public enum VerificationLevel {
    BASIC, PHONE, ID;

    public String wireValue() {
        return name().toLowerCase();
    }

    /** Ordinal comparison is intentional - BASIC < PHONE < ID is the real trust ordering. */
    public boolean atLeast(VerificationLevel required) {
        return this.ordinal() >= required.ordinal();
    }
}
