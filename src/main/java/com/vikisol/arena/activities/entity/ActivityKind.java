package com.vikisol.arena.activities.entity;

import java.util.Map;
import java.util.Set;

// What kind of activity a post is, and which type-specific detail keys it accepts (G7). Every
// kind also accepts COMMON. Values are short free text shown on the activity page.
public enum ActivityKind {
    SPORT(Set.of("sport", "format", "equipment")),
    FITNESS(Set.of("activity", "pace", "distance")),
    STUDY(Set.of("subject", "format")),
    MEETUP(Set.of("theme")),
    WORKSHOP(Set.of("topic", "materials")),
    COLLABORATION(Set.of("skillsNeeded", "commitment")),
    VOLUNTEER(Set.of("cause", "requirements")),
    OTHER(Set.of());

    public static final Set<String> COMMON = Set.of("level", "cost", "bring", "accessibility", "language");

    private final Set<String> keys;

    ActivityKind(Set<String> keys) {
        this.keys = keys;
    }

    public boolean accepts(String key) {
        return COMMON.contains(key) || keys.contains(key);
    }

    public Set<String> keys() {
        return keys;
    }

    public String wireValue() {
        return name().toLowerCase();
    }

    public static ActivityKind fromWire(String value) {
        for (ActivityKind k : values()) {
            if (k.name().equalsIgnoreCase(value == null ? "" : value.trim())) return k;
        }
        throw new IllegalArgumentException("kind must be one of " + String.join(", ",
                java.util.Arrays.stream(values()).map(ActivityKind::wireValue).toList()));
    }

    public static Map<String, Set<String>> catalogue() {
        java.util.Map<String, Set<String>> out = new java.util.LinkedHashMap<>();
        for (ActivityKind k : values()) out.put(k.wireValue(), k.keys);
        return out;
    }
}
