package com.vikisol.arena.common.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

// MARATHON-BE step 3 (production-safety self-check): FILE_SIGNING_SECRET has the exact same
// checked-in dev-fallback-secret risk JwtSecretGuard already closes for JWT_SECRET - a deploy
// that forgets to set it would sign every file URL with a secret anyone can read in this repo,
// letting a stranger forge a valid signature for any file path. Unlike JwtSecretGuard (which
// needs a separate, manually-set JWT_REQUIRE_REAL_SECRET flag), this ties directly to the active
// Spring profile - the same "forgot to set one flag" footgun that caused the SEED_ENABLED
// incident (see REPORTS-BE.md) is exactly what a profile-driven check avoids.
@Component
@RequiredArgsConstructor
public class FileSigningSecretGuard {

    private static final String DEV_FALLBACK = "local-dev-only-file-signing-secret-change-me";

    private final Environment environment;

    @Value("${app.storage.signing-secret}")
    private String signingSecret;

    @PostConstruct
    public void verify() {
        if (!environment.matchesProfiles("local") && DEV_FALLBACK.equals(signingSecret)) {
            throw new IllegalStateException(
                    "FILE_SIGNING_SECRET is still the checked-in local-dev fallback outside the 'local' profile - "
                            + "refusing to start with a publicly-known signing key for file URLs. Set FILE_SIGNING_SECRET "
                            + "to a real secret, or SPRING_PROFILES_ACTIVE=local if this really is a local/dev environment.");
        }
    }
}
