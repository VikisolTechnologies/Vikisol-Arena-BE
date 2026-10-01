package com.vikisol.arena.profile.entity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * FE-API-GAPS row 62: an industry is a row in arena_industries (V44), managed by staff at
 * /admin/industries, not a closed enum. The five original industries stay as constants for the
 * seeders and tests. The key (e.g. "ENGINEERING") is what's stored; the label is the wire value.
 * IndustryCatalogue keeps the label cache below in step with the table.
 */
public final class Industry implements Serializable {

    private static final Map<String, String> BUILT_IN = new LinkedHashMap<>();

    public static final Industry ENGINEERING = builtIn("ENGINEERING", "Engineering");
    public static final Industry DESIGN = builtIn("DESIGN", "Design");
    public static final Industry SALES = builtIn("SALES", "Sales");
    public static final Industry HEALTHCARE = builtIn("HEALTHCARE", "Healthcare");
    public static final Industry LOGISTICS = builtIn("LOGISTICS", "Logistics");

    // key -> label for every row (active or not), in display order. Replaced whole on refresh.
    private static volatile Map<String, String> labels = Map.copyOf(BUILT_IN);
    private static volatile List<Industry> all = List.of(ENGINEERING, DESIGN, SALES, HEALTHCARE, LOGISTICS);

    private final String key;

    private Industry(String key) {
        this.key = key;
    }

    private static Industry builtIn(String key, String label) {
        BUILT_IN.put(key, label);
        return new Industry(key);
    }

    public static Industry of(String key) {
        return new Industry(Objects.requireNonNull(key, "key"));
    }

    /** The stored key, e.g. "ENGINEERING". */
    public String name() {
        return key;
    }

    /** The label the frontend shows and sends, e.g. "Engineering". */
    public String wireValue() {
        String label = labels.get(key);
        if (label != null) return label;
        return BUILT_IN.getOrDefault(key, key);
    }

    /** Every known industry, active or not, in display order. */
    public static List<Industry> values() {
        return all;
    }

    /** Called by IndustryCatalogue with every row, in display order. */
    public static void refresh(Map<String, String> keyToLabel) {
        List<Industry> list = new ArrayList<>();
        keyToLabel.keySet().forEach(k -> list.add(new Industry(k)));
        labels = Map.copyOf(keyToLabel);
        all = List.copyOf(list);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Industry other && key.equals(other.key);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return key;
    }
}
