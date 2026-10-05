package com.vikisol.arena.common.util;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.exception.BadRequestException;

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

    // ARCHITECT-REVIEW-BE-1 (B11 item 17): a phone or Google sign-up has no date of birth at all
    // until onboarding's age gate runs - every write action (post, join, message, apply, connect)
    // must refuse such an account rather than silently letting age-less content/contact through.
    // Reads stay allowed; this is deliberately narrower than isAdult() (presence only, not the
    // 18+ rule itself, which activities already enforce separately via requireAdult()).
    // MARATHON-BE-2 step 1b item 6: "DOB_REQUIRED" is the stable code the frontend detects to
    // route straight to the DOB step, rather than parsing the human-readable message.
    public static final String DOB_REQUIRED_CODE = "DOB_REQUIRED";

    public static void requireDateOfBirth(User user) {
        if (user.getDateOfBirth() == null) {
            throw new BadRequestException("Add your date of birth to continue", DOB_REQUIRED_CODE);
        }
    }
}
