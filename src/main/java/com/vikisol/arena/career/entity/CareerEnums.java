package com.vikisol.arena.career.entity;

import java.util.Arrays;
import java.util.List;

// The closed vocabularies of the career setup screens (G18, G19). Wire values are lower-case.
public final class CareerEnums {

    private CareerEnums() {
    }

    public enum Intent { FIND_JOB, EXPLORE_QUIETLY, OFFER_SKILLS, HIRE_LOCALLY }

    public enum ExperienceLevel { ENTRY, JUNIOR, MID, SENIOR, LEAD }

    public enum WorkMode { ANY, ONSITE, HYBRID, REMOTE }

    public enum NoticePeriod { IMMEDIATE, DAYS_15, DAYS_30, DAYS_60, DAYS_90 }

    // Flow §6 (row 19).
    public enum WorkStatus { EMPLOYED, NOTICE, BETWEEN, STUDENT, FREELANCER }

    public enum Proficiency { LEARNING, WORKING, STRONG, EXPERT }

    public enum Shift { DAY, NIGHT, ROTATIONAL, FLEXIBLE }

    // Per-field visibility (row 19). PUBLIC: anyone who can see the published career profile.
    // EMPLOYERS_I_APPLY: only employers the person applied to. ONLY_ME: nobody else.
    public enum FieldVisibility { ONLY_ME, EMPLOYERS_I_APPLY, PUBLIC }

    // The frontend's lists (src/lib/intake/schemas/career.ts), matched case-insensitively and
    // stored as written here.
    public static final List<String> ROLE_FAMILIES = List.of("Engineering", "Design", "Product", "Data", "SAP", "Sales",
            "Marketing", "Operations", "Finance", "HR", "Support", "Other");
    public static final List<String> SAP_MODULES = List.of("FI", "CO", "MM", "SD", "PP", "QM", "PM", "HCM / SuccessFactors",
            "ABAP", "Basis", "BW / BI", "Ariba", "EWM", "S/4HANA Finance");
    public static final List<String> COMPANY_SIZES = List.of("1–10", "11–50", "51–200", "201–1000", "1000+");
    public static final List<String> DEGREES = List.of("10th", "12th", "Diploma", "Bachelor's", "Master's", "PhD", "Other");

    public static String pick(List<String> allowed, String value, String label) {
        String v = value == null ? "" : value.trim().replace('-', '–');
        for (String a : allowed) {
            if (a.equalsIgnoreCase(v) || a.equalsIgnoreCase(value == null ? "" : value.trim())) return a;
        }
        throw new IllegalArgumentException(label + " must be one of " + String.join(", ", allowed));
    }

    // Also takes the frontend's labels: "Immediate", "15 days".
    public static NoticePeriod parseNotice(String value) {
        if (value == null) return null;
        String v = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (v.matches("\\d+ days")) v = "days_" + v.substring(0, v.indexOf(' '));
        return parse(NoticePeriod.class, v, "noticePeriod");
    }

    // Who may see the expected compensation. PRIVATE is the default and means nobody but you.
    // ON_APPLICATION: only employers you apply to. EMPLOYERS: any employer who can see your
    // full profile (you applied to them, or they unlocked you) or your published career profile.
    public enum CompensationVisibility { PRIVATE, ON_APPLICATION, EMPLOYERS }

    public static String wire(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase();
    }

    public static <E extends Enum<E>> E parse(Class<E> type, String value, String label) {
        if (value == null) return null;
        String v = value.trim().toUpperCase().replace('-', '_');
        for (E e : type.getEnumConstants()) {
            if (e.name().equals(v)) return e;
        }
        throw new IllegalArgumentException(label + " must be one of " + String.join(", ",
                Arrays.stream(type.getEnumConstants()).map(CareerEnums::wire).toList()));
    }
}
