package com.vikisol.arena.career.entity;

import java.util.Arrays;

// The closed vocabularies of the career setup screens (G18, G19). Wire values are lower-case.
public final class CareerEnums {

    private CareerEnums() {
    }

    public enum Intent { FIND_JOB, EXPLORE_QUIETLY, OFFER_SKILLS, HIRE_LOCALLY }

    public enum ExperienceLevel { ENTRY, JUNIOR, MID, SENIOR, LEAD }

    public enum WorkMode { ANY, ONSITE, HYBRID, REMOTE }

    public enum NoticePeriod { IMMEDIATE, DAYS_15, DAYS_30, DAYS_60, DAYS_90 }

    // Who may see the expected compensation. PRIVATE is the default and means nobody but you.
    // ON_APPLICATION: only employers you apply to. EMPLOYERS: any employer who can see your
    // full profile (you applied to them, or they unlocked you) or your published career profile.
    public enum CompensationVisibility { PRIVATE, ON_APPLICATION, EMPLOYERS }

    public static String wire(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase();
    }

    public static <E extends Enum<E>> E parse(Class<E> type, String value, String label) {
        if (value == null) return null;
        String v = value.trim().toUpperCase();
        for (E e : type.getEnumConstants()) {
            if (e.name().equals(v)) return e;
        }
        throw new IllegalArgumentException(label + " must be one of " + String.join(", ",
                Arrays.stream(type.getEnumConstants()).map(CareerEnums::wire).toList()));
    }
}
