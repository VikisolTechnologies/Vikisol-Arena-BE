package com.vikisol.arena.career.service;

import com.vikisol.arena.career.entity.CareerEnums.CompensationVisibility;
import com.vikisol.arena.career.entity.CareerProfile;

// The one rule for whether an employer may see someone's pay (G21): current CTC, expected CTC
// and the career profile's expected range alike. Private unless the person chose otherwise;
// no career profile at all counts as private.
public final class CompensationPolicy {

    private CompensationPolicy() {
    }

    public static boolean employerMaySee(CareerProfile career, boolean appliedToThisEmployer, boolean unlockedByThisEmployer) {
        CompensationVisibility v = career == null ? CompensationVisibility.PRIVATE : career.getCompensationVisibility();
        return switch (v) {
            case PRIVATE -> false;
            case ON_APPLICATION -> appliedToThisEmployer;
            case EMPLOYERS -> appliedToThisEmployer || unlockedByThisEmployer || career.getPublishedAt() != null;
        };
    }
}
