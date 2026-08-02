package com.vikisol.arena.marketplace.entity;

// arena-web's mock kept bid status in a *separate* localStorage list (MyBidRecord in myBids.ts)
// from the Bid itself - the exact "two parallel records" pattern AUDIT.md flagged for
// Applicant/Application. Folded into the Bid entity here instead: one row, one status.
public enum BidStatus {
    PENDING, SHORTLISTED, WON, LOST;

    public String wireValue() {
        return name().toLowerCase();
    }
}
