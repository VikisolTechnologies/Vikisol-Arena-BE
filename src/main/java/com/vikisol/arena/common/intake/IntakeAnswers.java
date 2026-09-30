package com.vikisol.arena.common.intake;

import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.policy.ProtectedAttributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// The answers to the frontend's intake questions (ARENA-APP-FLOW §2), stored as
// {key: text | number | yes/no | list of short texts}. The questions themselves live in the
// frontend's schemas; the backend checks the shape and sizes and runs the protected-attribute
// guard on every piece of free text.
public final class IntakeAnswers {

    public static final int MAX_KEYS = 20;
    public static final int MAX_TEXT = 300;

    private IntakeAnswers() {
    }

    public static Map<String, Object> clean(Map<String, Object> raw, String where) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        if (raw == null) return out;
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            String key = e.getKey() == null ? "" : e.getKey().trim();
            if (key.isEmpty() || key.length() > 40 || !key.matches("[A-Za-z][A-Za-z0-9_]*")) {
                throw new BadRequestException("'" + key + "' isn't a valid answer key");
            }
            ProtectedAttributes.reject(where, key.replace('_', ' '));
            Object v = e.getValue();
            if (v == null) continue;
            if (v instanceof String str) {
                String t = str.trim();
                if (t.isEmpty()) continue;
                if (t.length() > MAX_TEXT) throw new BadRequestException("'" + key + "' can be at most " + MAX_TEXT + " characters");
                ProtectedAttributes.reject(where, t);
                out.put(key, t);
            } else if (v instanceof Number || v instanceof Boolean) {
                out.put(key, v);
            } else if (v instanceof List<?> list) {
                out.put(key, cleanList(list.stream().map(o -> o == null ? "" : o.toString()).toList(), 10, 60, "'" + key + "'", where));
            } else {
                throw new BadRequestException("'" + key + "' must be text, a number, yes/no or a list");
            }
        }
        if (out.size() > MAX_KEYS) throw new BadRequestException("Too many details");
        return out;
    }

    public static List<String> cleanList(List<String> raw, int maxItems, int maxLength, String label, String where) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String item : raw) {
            String t = item == null ? "" : item.trim();
            if (t.isEmpty()) continue;
            if (t.length() > maxLength) throw new BadRequestException("Each item in " + label + " can be at most " + maxLength + " characters");
            ProtectedAttributes.reject(where, t);
            if (!out.contains(t)) out.add(t);
        }
        if (out.size() > maxItems) throw new BadRequestException(label + " can have at most " + maxItems + " items");
        return out;
    }
}
