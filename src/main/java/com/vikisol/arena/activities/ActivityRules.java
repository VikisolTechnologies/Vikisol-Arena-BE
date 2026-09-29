package com.vikisol.arena.activities;

import java.time.Duration;

// Shared activity timings.
public final class ActivityRules {

    // G11: a participant marked no-show can dispute it for this long after the host records it,
    // and until it has passed (or while a dispute is open) the no-show never counts against them.
    public static final Duration DISPUTE_WINDOW = Duration.ofHours(72);

    // G10: self check-in opens this long before the start...
    public static final Duration CHECK_IN_OPENS_BEFORE = Duration.ofHours(1);
    // ...and closes at the end time, or this long after the start when there is no end time.
    public static final Duration CHECK_IN_DEFAULT_LENGTH = Duration.ofHours(6);

    public static final int MAX_QUESTIONS = 3;

    private ActivityRules() {
    }
}
