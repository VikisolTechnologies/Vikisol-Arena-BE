package com.vikisol.arena.needs.entity;

import java.time.Duration;

// The fixed choices in the need and offer intake (ARENA-APP-FLOW §4, FE-API-GAPS row 27), with
// the frontend's hyphenated ids on the wire.
public final class NeedIntake {

    private NeedIntake() {
    }

    public enum Urgency { TODAY, WEEK, FLEXIBLE }

    // COSTS ("I'll cover costs") is for needs only. No payments happen in Arena.
    public enum HelpType { FREE, EXCHANGE, COSTS }

    public enum OfferDay { WEEKDAYS, WEEKENDS, EVENINGS }

    // How many requests an offer's owner accepts in a rolling window. Accepting past it is refused.
    public enum OfferLimit {
        ONCE_A_WEEK(1, Duration.ofDays(7)),
        TWICE_A_WEEK(2, Duration.ofDays(7)),
        A_FEW_TIMES_A_MONTH(3, Duration.ofDays(30)),
        NO_LIMIT(Integer.MAX_VALUE, Duration.ZERO);

        public final int max;
        public final Duration window;

        OfferLimit(int max, Duration window) {
            this.max = max;
            this.window = window;
        }
    }

    public static <E extends Enum<E>> E parse(Class<E> type, String value, String label) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim().toUpperCase().replace('-', '_');
        for (E e : type.getEnumConstants()) {
            if (e.name().equals(v)) return e;
        }
        throw new IllegalArgumentException(label + " must be one of " + String.join(", ",
                java.util.Arrays.stream(type.getEnumConstants()).map(NeedIntake::wire).toList()));
    }

    public static String wire(Enum<?> e) {
        return e == null ? null : e.name().toLowerCase().replace('_', '-');
    }
}
