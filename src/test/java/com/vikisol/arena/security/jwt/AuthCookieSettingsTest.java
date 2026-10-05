package com.vikisol.arena.security.jwt;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class AuthCookieSettingsTest {

    @Test
    void productionDefaultsAreSecureLaxAndHostOnly() {
        SessionCookieHelper session = sessionHelper(new MockEnvironment(), "", "Lax", "");
        RefreshCookieHelper refresh = refreshHelper(new MockEnvironment(), "", "Lax");

        String sessionCookie = setCookie(session, "access-token");
        String refreshCookie = setCookie(refresh, "refresh-token");

        assertThat(sessionCookie)
                .contains("arena_session=access-token")
                .contains("Path=/")
                .contains("SameSite=Lax")
                .contains("Secure")
                .doesNotContain("Domain=");
        assertThat(refreshCookie)
                .contains("arena_refresh=refresh-token")
                .contains("Path=/api/v1/auth")
                .contains("SameSite=Lax")
                .contains("Secure")
                .doesNotContain("Domain=");
    }

    @Test
    void localProfileDefaultsToNonSecureCookiesForHttpLocalhost() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        SessionCookieHelper session = sessionHelper(environment, "", "Lax", "");

        assertThat(setCookie(session, "access-token")).doesNotContain("Secure");
    }

    @Test
    void secureSameSiteAndDomainRemainExplicitlyConfigurable() {
        SessionCookieHelper session = sessionHelper(new MockEnvironment(), "false", "Strict", ".example.test");

        String cookie = setCookie(session, "access-token");

        assertThat(cookie)
                .contains("SameSite=Strict")
                .contains("Domain=.example.test")
                .doesNotContain("Secure");
    }

    private static SessionCookieHelper sessionHelper(MockEnvironment environment, String secure, String sameSite, String domain) {
        SessionCookieHelper helper = new SessionCookieHelper(environment);
        ReflectionTestUtils.setField(helper, "accessExpirationMs", 900000L);
        ReflectionTestUtils.setField(helper, "cookieSecure", secure);
        ReflectionTestUtils.setField(helper, "cookieSameSite", sameSite);
        ReflectionTestUtils.setField(helper, "cookieDomain", domain);
        return helper;
    }

    private static RefreshCookieHelper refreshHelper(MockEnvironment environment, String secure, String sameSite) {
        RefreshCookieHelper helper = new RefreshCookieHelper(environment);
        ReflectionTestUtils.setField(helper, "refreshExpirationMs", 7776000000L);
        ReflectionTestUtils.setField(helper, "cookieSecure", secure);
        ReflectionTestUtils.setField(helper, "cookieSameSite", sameSite);
        return helper;
    }

    private static String setCookie(SessionCookieHelper helper, String token) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        helper.set(response, token);
        return response.getHeader("Set-Cookie");
    }

    private static String setCookie(RefreshCookieHelper helper, String token) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        helper.set(response, token);
        return response.getHeader("Set-Cookie");
    }
}
