package com.vikisol.arena.config;

import io.sentry.SentryEvent;
import io.sentry.protocol.Request;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// Session Replay does not exist on the Java SDK. This drops request cookies, auth headers, and
// the body before an event is sent. The DSN stays in the environment.
public final class SentryScrub {

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "cookie", "set-cookie", "proxy-authorization");

    private SentryScrub() {
    }

    public static SentryEvent scrub(SentryEvent event) {
        Request request = event.getRequest();
        if (request == null) return event;
        request.setCookies(null);
        request.setData(null);
        Map<String, String> headers = request.getHeaders();
        if (headers != null) {
            Map<String, String> kept = new LinkedHashMap<>();
            headers.forEach((name, value) -> {
                if (!SENSITIVE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                    kept.put(name, value);
                }
            });
            request.setHeaders(kept);
        }
        return event;
    }
}
