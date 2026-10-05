package com.vikisol.arena.security.proxy;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TrustedProxyFilterTest {

    private final FilterChain chain = mock(FilterChain.class);

    @Test
    void missingSecretIsForbiddenWhenProxySecretIsConfigured() throws Exception {
        TrustedProxyFilter filter = filter("shared-proxy-secret", new MockEnvironment());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/profile/me");
        request.addHeader(TrustedProxyFilter.CLIENT_IP_HEADER, "203.0.113.10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void wrongSecretIsForbidden() throws Exception {
        TrustedProxyFilter filter = filter("shared-proxy-secret", new MockEnvironment());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/profile/me");
        request.addHeader(TrustedProxyFilter.PROXY_SECRET_HEADER, "not-it");
        request.addHeader(TrustedProxyFilter.CLIENT_IP_HEADER, "203.0.113.10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void spoofedClientIpWithoutSecretIsRefusedBeforeItCanBeTrusted() throws Exception {
        TrustedProxyFilter filter = filter("shared-proxy-secret", new MockEnvironment());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/signup");
        request.addHeader(TrustedProxyFilter.CLIENT_IP_HEADER, "198.51.100.50");
        request.addHeader("X-Forwarded-For", "198.51.100.60");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(TrustedProxyFilter.trustedClientIp(request)).isNull();
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void acceptedRequestStoresOnlyAValidatedClientIp() throws Exception {
        TrustedProxyFilter filter = filter("shared-proxy-secret", new MockEnvironment());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/signup");
        request.addHeader(TrustedProxyFilter.PROXY_SECRET_HEADER, "shared-proxy-secret");
        request.addHeader(TrustedProxyFilter.CLIENT_IP_HEADER, "198.51.100.25");
        request.addHeader("X-Forwarded-For", "198.51.100.99");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(TrustedProxyFilter.trustedClientIp(request)).isEqualTo("198.51.100.25");
        verify(chain).doFilter(request, response);
    }

    @Test
    void validSecretWithInvalidClientIpIsBadRequest() throws Exception {
        TrustedProxyFilter filter = filter("shared-proxy-secret", new MockEnvironment());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/profile/me");
        request.addHeader(TrustedProxyFilter.PROXY_SECRET_HEADER, "shared-proxy-secret");
        request.addHeader(TrustedProxyFilter.CLIENT_IP_HEADER, "not-an-ip");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void healthBypassesTheProxySecretForExternalMonitoring() throws Exception {
        TrustedProxyFilter filter = filter("shared-proxy-secret", new MockEnvironment());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    void localProfileBypassesTheProxySecret() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        TrustedProxyFilter filter = filter("shared-proxy-secret", environment);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/profile/me");
        request.addHeader(TrustedProxyFilter.CLIENT_IP_HEADER, "198.51.100.25");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(TrustedProxyFilter.trustedClientIp(request)).isNull();
        verify(chain).doFilter(request, response);
    }

    private static TrustedProxyFilter filter(String secret, MockEnvironment environment) {
        TrustedProxyFilter filter = new TrustedProxyFilter(environment);
        ReflectionTestUtils.setField(filter, "proxySecret", secret);
        return filter;
    }
}
