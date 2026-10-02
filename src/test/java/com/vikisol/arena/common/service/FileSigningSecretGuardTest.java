package com.vikisol.arena.common.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// MARATHON-BE step 3: FILE_SIGNING_SECRET must never be left on the checked-in dev fallback
// outside the 'local' profile - same risk JwtSecretGuard already closes for JWT_SECRET, but tied
// to the active profile instead of a separately-set flag.
class FileSigningSecretGuardTest {

    @Test
    void refusesTheDevFallbackOutsideLocal() {
        FileSigningSecretGuard guard = new FileSigningSecretGuard(new MockEnvironment());
        ReflectionTestUtils.setField(guard, "signingSecret", "local-dev-only-file-signing-secret-change-me");
        assertThatThrownBy(guard::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FILE_SIGNING_SECRET");
    }

    @Test
    void allowsTheDevFallbackOnTheLocalProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        FileSigningSecretGuard guard = new FileSigningSecretGuard(env);
        ReflectionTestUtils.setField(guard, "signingSecret", "local-dev-only-file-signing-secret-change-me");
        assertThatNoException().isThrownBy(guard::verify);
    }

    @Test
    void allowsARealSecretOutsideLocal() {
        FileSigningSecretGuard guard = new FileSigningSecretGuard(new MockEnvironment());
        ReflectionTestUtils.setField(guard, "signingSecret", "a-real-production-signing-secret-xyz");
        assertThatNoException().isThrownBy(guard::verify);
    }
}
