package com.vikisol.arena.config;

import io.sentry.SentryEvent;
import io.sentry.protocol.Request;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SentryScrubTest {

    @Test
    void stripsCookiesAuthHeadersAndBody() {
        Request request = new Request();
        request.setCookies("session=abc");
        request.setData("password=secret");
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer token");
        headers.put("Cookie", "session=abc");
        headers.put("Accept", "application/json");
        request.setHeaders(headers);
        SentryEvent event = new SentryEvent();
        event.setRequest(request);

        SentryEvent scrubbed = SentryScrub.scrub(event);

        assertNull(scrubbed.getRequest().getCookies());
        assertNull(scrubbed.getRequest().getData());
        assertNull(scrubbed.getRequest().getHeaders().get("Authorization"));
        assertNull(scrubbed.getRequest().getHeaders().get("Cookie"));
        assertEquals("application/json", scrubbed.getRequest().getHeaders().get("Accept"));
    }
}
