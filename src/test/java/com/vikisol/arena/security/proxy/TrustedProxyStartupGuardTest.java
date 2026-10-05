package com.vikisol.arena.security.proxy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class TrustedProxyStartupGuardTest {

    @Test
    void nonLocalBlankProxySecretFailsStartupByDefault() {
        TrustedProxyStartupGuard guard = guard(new MockEnvironment(), "", true);

        assertThatThrownBy(guard::verifyProxySecretConfigured)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ARENA_PROXY_SECRET")
                .hasMessageContaining("app.proxy.required=false");
    }

    @Test
    void nonLocalBlankProxySecretCanStartOnlyWithExplicitEscapeHatch(CapturedOutput output) {
        TrustedProxyStartupGuard guard = guard(new MockEnvironment(), "  ", false);

        guard.verifyProxySecretConfigured();

        assertThat(output).contains("ARENA_PROXY_SECRET is blank")
                .contains("app.proxy.required=false")
                .contains("trusted proxy enforcement is disabled");
    }

    @Test
    void localProfileCanStartWithBlankProxySecret() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        TrustedProxyStartupGuard guard = guard(environment, "", true);

        guard.verifyProxySecretConfigured();
    }

    private static TrustedProxyStartupGuard guard(MockEnvironment environment, String secret, boolean required) {
        TrustedProxyStartupGuard guard = new TrustedProxyStartupGuard(environment);
        ReflectionTestUtils.setField(guard, "proxySecret", secret);
        ReflectionTestUtils.setField(guard, "proxyRequired", required);
        return guard;
    }
}
