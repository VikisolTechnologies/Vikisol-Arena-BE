package com.vikisol.arena.common.util;

import java.time.LocalDate;
import java.time.Period;

/** Shared by VerificationService (status display) and PostService (the actual ACTIVITY
 * create/join gate) - one formula, not two copies drifting apart. */
public final class AgeUtil {

    public static final int MINIMUM_AGE = 18;

    private AgeUtil() {
    }

    public static boolean isAdult(LocalDate dateOfBirth) {
        return dateOfBirth != null && Period.between(dateOfBirth, LocalDate.now()).getYears() >= MINIMUM_AGE;
    }
}
