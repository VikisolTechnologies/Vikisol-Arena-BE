package com.vikisol.arena.security.ratelimit;

import com.vikisol.arena.security.proxy.TrustedProxyFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Architect decision 30 Sep 2026: reading messages has its own 60-per-minute bucket (the frontend
// polls the open thread every 5 s and the list every 30 s); sending keeps the 30-per-minute one.
class MessagingRateLimitTest {

    private final RateLimitFilter filter = new RateLimitFilter(null);

    {
        ReflectionTestUtils.setField(filter, "messagingPerMinute", 30);
        ReflectionTestUtils.setField(filter, "messagingReadPerMinute", 60);
        ReflectionTestUtils.setField(filter, "defaultPerMinute", 120);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
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

    @Test
    void trustedProxyClientIpsUseSeparateUnauthenticatedRateLimitBuckets() throws Exception {
        @SuppressWarnings("unchecked")
        org.springframework.data.redis.core.ValueOperations<String, String> ops =
                mock(org.springframework.data.redis.core.ValueOperations.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(any(String.class))).thenReturn(1L);

        RateLimitFilter rateLimit = new RateLimitFilter(redis);
        ReflectionTestUtils.setField(rateLimit, "enabled", true);
        ReflectionTestUtils.setField(rateLimit, "authPerMinute", 10);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest first = new MockHttpServletRequest("POST", "/api/v1/auth/signup");
        first.setRemoteAddr("10.0.0.10");
        first.setAttribute(TrustedProxyFilter.CLIENT_IP_ATTRIBUTE, "198.51.100.10");
        rateLimit.doFilter(first, new MockHttpServletResponse(), chain);

        MockHttpServletRequest second = new MockHttpServletRequest("POST", "/api/v1/auth/signup");
        second.setRemoteAddr("10.0.0.10");
        second.setAttribute(TrustedProxyFilter.CLIENT_IP_ATTRIBUTE, "198.51.100.11");
        rateLimit.doFilter(second, new MockHttpServletResponse(), chain);

        verify(ops).increment(eq("ratelimit:auth:ip:198.51.100.10"));
        verify(ops).increment(eq("ratelimit:auth:ip:198.51.100.11"));
    }
}
