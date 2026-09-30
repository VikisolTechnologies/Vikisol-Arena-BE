package com.vikisol.arena.career.service;

// The one rule for whether an employer may see someone's pay (G21): current CTC, expected CTC
// and the career profile's expected range alike. Per ARENA-APP-FLOW §6: private ("only me") by
// default, and shared with an employer only when the person applies to them and ticks "include
// my CTC" on that application. Nothing else - publishing, an unlock, a setting - shares it.
public final class CompensationPolicy {

    private CompensationPolicy() {
    }

    public static boolean employerMaySee(boolean ctcIncludedOnAnApplicationToThisEmployer) {
        return ctcIncludedOnAnApplicationToThisEmployer;
    }
}
