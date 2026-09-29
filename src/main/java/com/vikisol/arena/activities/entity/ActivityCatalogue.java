package com.vikisol.arena.activities.entity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// The activity categories and subtypes from ARENA-APP-FLOW §3 A1 (FE-API-GAPS row 23). The type-
// specific questions per subtype live in the frontend's intake schemas; the backend checks the
// category/subtype pair and stores the answers. "other" takes a free-text subtype.
public final class ActivityCatalogue {

    public enum Category { SPORTS, FITNESS, OUTDOORS, LEARNING, ARTS, GAMES, FOOD, COMMUNITY, OTHER }

    public enum Level { BEGINNER, INTERMEDIATE, ADVANCED, ALL }

    public enum CostType { FREE, SHARED }

    public enum Repeat { ONCE, WEEKLY }

    // NEARBY: shown in Feed, Discover, Map and search. LINK: only people with the link see it.
    public enum Reach { NEARBY, LINK }

    public static final Map<Category, List<String>> SUBTYPES = new LinkedHashMap<>();

    static {
        SUBTYPES.put(Category.SPORTS, List.of("cricket", "badminton", "football", "volleyball", "basketball", "tennis",
                "table_tennis", "pickleball", "swimming"));
        SUBTYPES.put(Category.FITNESS, List.of("running", "walking", "cycling", "yoga", "gym_buddy"));
        SUBTYPES.put(Category.OUTDOORS, List.of("trekking", "hiking", "camping", "birdwatching", "photo_walk"));
        SUBTYPES.put(Category.LEARNING, List.of("workshop", "study_group", "language_exchange", "book_club", "tech_meetup"));
        SUBTYPES.put(Category.ARTS, List.of("music_jam", "pottery", "painting", "dance", "theatre"));
        SUBTYPES.put(Category.GAMES, List.of("board_games", "chess", "quiz_night"));
        SUBTYPES.put(Category.FOOD, List.of("potluck", "cook_together", "food_walk"));
        SUBTYPES.put(Category.COMMUNITY, List.of("clean_up", "tree_planting", "volunteering", "donation_drive"));
        SUBTYPES.put(Category.OTHER, List.of());
    }

    // Flow §3 (Trekking): joiners give an emergency contact the host sees after approval; it is
    // deleted after the trek.
    public static final Set<String> NEEDS_EMERGENCY_CONTACT = Set.of("trekking");

    private ActivityCatalogue() {
    }

    public static <E extends Enum<E>> E parse(Class<E> type, String value, String label) {
        if (value == null) return null;
        String v = value.trim().toUpperCase();
        for (E e : type.getEnumConstants()) {
            if (e.name().equals(v)) return e;
        }
        throw new IllegalArgumentException(label + " must be one of " + String.join(", ",
                java.util.Arrays.stream(type.getEnumConstants()).map(ActivityCatalogue::wire).toList()));
    }

    public static String wire(Enum<?> e) {
        return e == null ? null : e.name().toLowerCase();
    }

    public static Map<String, List<String>> catalogue() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        SUBTYPES.forEach((c, s) -> out.put(wire(c), s));
        return out;
    }
}
