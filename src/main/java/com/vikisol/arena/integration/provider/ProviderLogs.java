package com.vikisol.arena.integration.provider;

import java.util.regex.Pattern;

// Makes a provider's error text safe to log: providers echo back recipients, and a failed OTP
// send can echo the code. Masks email addresses, bearer tokens and other long key-like strings,
// and any run of 4+ digits (phone numbers, OTP codes, account ids), then truncates.
public final class ProviderLogs {

    static final int MAX_LENGTH = 500;

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+\\S+");
    private static final Pattern KEY_LIKE = Pattern.compile("[A-Za-z0-9_\\-]{24,}");
    private static final Pattern DIGITS = Pattern.compile("\\+?\\d[\\d \\-]{2,}\\d");

    private ProviderLogs() {
    }

    public static String redact(String text) {
        if (text == null) return "";
        String out = EMAIL.matcher(text).replaceAll("[email]");
        out = BEARER.matcher(out).replaceAll("Bearer [redacted]");
        out = KEY_LIKE.matcher(out).replaceAll("[redacted]");
        out = DIGITS.matcher(out).replaceAll(m -> m.group().replaceAll("\\D", "").length() >= 4 ? "[number]" : m.group());
        return out.length() > MAX_LENGTH ? out.substring(0, MAX_LENGTH) + "…" : out;
    }
}
