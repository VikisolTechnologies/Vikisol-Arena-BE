package com.vikisol.arena.common.util;

import java.util.function.Predicate;

// ARENA-MASTER-ARCHITECTURE.md PART 6/7.12 - every user needs a stable, URL-safe `handle` for
// `/people/{handle}`. Generated once at signup from the display name (never re-derived from
// email - a name is what a public profile URL should read like), lowercased/slugified, with a
// numeric suffix appended only on collision so the common case stays a clean "@janedoe".
public final class HandleGenerator {

    private HandleGenerator() {
    }

    public static String generate(String displayName, Predicate<String> handleTaken) {
        String base = slugify(displayName);
        if (base.isBlank()) base = "user";
        if (!handleTaken.test(base)) return base;
        for (int suffix = 2; suffix < 10_000; suffix++) {
            String candidate = base + suffix;
            if (!handleTaken.test(candidate)) return candidate;
        }
        // Astronomically unlikely (9998 collisions on one base slug) - a random suffix guarantees
        // termination instead of leaving handle generation able to loop forever.
        return base + System.currentTimeMillis();
    }

    private static String slugify(String input) {
        if (input == null) return "";
        String lower = input.toLowerCase().trim();
        String stripped = lower.replaceAll("[^a-z0-9]+", "");
        return stripped.length() > 30 ? stripped.substring(0, 30) : stripped;
    }
}
