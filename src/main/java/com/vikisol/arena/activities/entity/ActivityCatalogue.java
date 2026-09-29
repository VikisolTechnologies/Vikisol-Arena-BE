package com.vikisol.arena.activities.entity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// The activity categories and subtypes from ARENA-APP-FLOW §3 A1 (FE-API-GAPS row 23), with the
// frontend's ids (src/lib/activities/taxonomy.ts): hyphenated, e.g. "table-tennis", "all-levels".
// Underscores are accepted too. The type-specific questions per subtype live in the frontend's
// intake schemas; the backend checks the category/subtype pair and stores the answers. "other"
// takes a free-text subtype.
public final class ActivityCatalogue {

    public enum Category { SPORTS, FITNESS, OUTDOORS, LEARNING, ARTS, GAMES, FOOD, COMMUNITY, OTHER }

    public enum Level { BEGINNER, INTERMEDIATE, ADVANCED, ALL_LEVELS }

    public enum CostType { FREE, SHARED }

    public enum Repeat { ONCE, WEEKLY }

    // NEARBY: shown in Feed, Discover, Map and search. LINK: only people with the link see it.
    public enum Reach { NEARBY, LINK }

    public static final Map<Category, List<String>> SUBTYPES = new LinkedHashMap<>();

    static {
        SUBTYPES.put(Category.SPORTS, List.of("cricket", "badminton", "football", "volleyball", "basketball", "tennis",
                "table-tennis", "pickleball", "swimming"));
        SUBTYPES.put(Category.FITNESS, List.of("running", "walking", "cycling", "yoga", "gym-buddy"));
        SUBTYPES.put(Category.OUTDOORS, List.of("trekking", "hiking", "camping", "birdwatching", "photo-walk"));
        SUBTYPES.put(Category.LEARNING, List.of("workshop", "study-group", "language-exchange", "book-club", "tech-meetup"));
        SUBTYPES.put(Category.ARTS, List.of("music-jam", "pottery", "painting", "dance", "theatre"));
        SUBTYPES.put(Category.GAMES, List.of("board-games", "chess", "quiz-night"));
        SUBTYPES.put(Category.FOOD, List.of("potluck", "cook-together", "food-walk"));
        SUBTYPES.put(Category.COMMUNITY, List.of("clean-up", "tree-planting", "volunteering", "donation-drive"));
        SUBTYPES.put(Category.OTHER, List.of());
    }

    // Flow §3 (Trekking): joiners give an emergency contact the host sees after approval; it is
    // deleted after the trek.
    public static final Set<String> NEEDS_EMERGENCY_CONTACT = Set.of("trekking");

    private ActivityCatalogue() {
    }

    public static <E extends Enum<E>> E parse(Class<E> type, String value, String label) {
        if (value == null) return null;
        String v = value.trim().toUpperCase().replace('-', '_');
        if (type == Level.class && v.equals("ALL")) v = "ALL_LEVELS";
        for (E e : type.getEnumConstants()) {
            if (e.name().equals(v)) return e;
        }
        throw new IllegalArgumentException(label + " must be one of " + String.join(", ",
                java.util.Arrays.stream(type.getEnumConstants()).map(ActivityCatalogue::wire).toList()));
    }

    public static String wire(Enum<?> e) {
        return e == null ? null : e.name().toLowerCase().replace('_', '-');
    }

    // "table_tennis" and "table-tennis" are the same subtype.
    public static String normaliseSubtype(String subtype) {
        return subtype == null ? null : subtype.trim().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }

    public static Map<String, List<String>> catalogue() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        SUBTYPES.forEach((c, s) -> out.put(wire(c), s));
        return out;
    }
}
