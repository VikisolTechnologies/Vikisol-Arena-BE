package com.vikisol.arena.security.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

// Architect decision 30 Sep 2026: reading messages has its own 60-per-minute bucket (the frontend
// polls the open thread every 5 s and the list every 30 s); sending keeps the 30-per-minute one.
class MessagingRateLimitTest {

    private final RateLimitFilter filter = new RateLimitFilter(null);

    {
        ReflectionTestUtils.setField(filter, "messagingPerMinute", 30);
        ReflectionTestUtils.setField(filter, "messagingReadPerMinute", 60);
        ReflectionTestUtils.setField(filter, "defaultPerMinute", 120);
    }

    private RateLimitFilter.Bucket bucket(String method, String path) {
        return filter.bucketFor(new MockHttpServletRequest(method, path));
    }

    @Test
    void readsAndSendsCountSeparately() {
        assertThat(bucket("GET", "/api/v1/messages/conversations")).isEqualTo(new RateLimitFilter.Bucket("messaging-read", 60));
        assertThat(bucket("GET", "/api/v1/messages/conversations/abc/messages")).isEqualTo(new RateLimitFilter.Bucket("messaging-read", 60));
        assertThat(bucket("POST", "/api/v1/messages/conversations/abc/messages")).isEqualTo(new RateLimitFilter.Bucket("messaging", 30));
    }

    @Test
    void theDefaultsMatchTheDecision() throws Exception {
        String yml = new String(getClass().getResourceAsStream("/application.yml").readAllBytes());
        assertThat(yml).contains("messaging-read-per-minute: ${RATE_LIMIT_MESSAGING_READ_PER_MIN:60}")
                .contains("messaging-per-minute: ${RATE_LIMIT_MESSAGING_PER_MIN:30}");
    }
}
