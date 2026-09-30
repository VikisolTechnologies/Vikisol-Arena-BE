package com.vikisol.arena.search;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Word matching shared by every search source. A query matches when EVERY word in it appears
 * somewhere in the item (so "react hyderabad" narrows rather than widens), and ranks higher when
 * words land in the item's title. Case-insensitive, prefix-friendly ("bask" finds "basketball").
 *
 * In-memory on purpose while Arena's content is in the hundreds - swapping in Postgres full-text
 * search later only changes where candidates come from, not this ranking contract.
 */
public final class SearchText {

    private SearchText() {
    }

    public static List<String> terms(String query) {
        if (query == null) return List.of();
        return Arrays.stream(query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}+#.]+"))
                .map(t -> t.replaceAll("^\\.+|\\.+$", ""))
                .filter(t -> t.length() >= 2)
                .distinct()
                .limit(8)
                .toList();
    }

    /** True when the word is plain ASCII, so a database lower()/LIKE matches it exactly as Java does. */
    public static boolean isAscii(String term) {
        return term.chars().allMatch(c -> c < 128);
    }

    /** '%word%' for a database LIKE, with LIKE's own wildcards escaped (terms can't contain them today). */
    public static String likePattern(String term) {
        return "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    /** Joins the searchable fields into one lowercase haystack; nulls are skipped. */
    public static String haystack(Object... parts) {
        return Stream.of(parts)
                .filter(Objects::nonNull)
                .flatMap(p -> p instanceof Collection<?> c ? c.stream().map(String::valueOf) : Stream.of(String.valueOf(p)))
                .collect(Collectors.joining(" "))
                .toLowerCase(Locale.ROOT);
    }

    /** 0 = no match (some word missing). Otherwise higher is better: title hits count triple. */
    public static int score(List<String> terms, String title, String rest) {
        if (terms.isEmpty()) return 0;
        String t = title == null ? "" : title.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            boolean inTitle = t.contains(term);
            if (!inTitle && !rest.contains(term)) return 0;
            score += inTitle ? 3 : 1;
            // Whole-word title hit ("react" in "React developer", not "reactive") ranks above a partial one.
            if (inTitle && wholeWord(t, term)) score += 1;
        }
        return score;
    }

    // True when `term` occurs in `text` with no letter or digit right before or after it. Same
    // result as the regex this replaced (PERFORMANCE.md: compiling it per post was a hot spot).
    static boolean wholeWord(String text, String term) {
        for (int i = text.indexOf(term); i >= 0; i = text.indexOf(term, i + 1)) {
            int end = i + term.length();
            boolean before = i == 0 || !isWordChar(text.codePointBefore(i));
            boolean after = end == text.length() || !isWordChar(text.codePointAt(end));
            if (before && after) return true;
        }
        return false;
    }

    private static boolean isWordChar(int cp) {
        return Character.isLetter(cp) || switch (Character.getType(cp)) {
            case Character.DECIMAL_DIGIT_NUMBER, Character.LETTER_NUMBER, Character.OTHER_NUMBER -> true;
            default -> false;
        };
    }
}
