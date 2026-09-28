package com.vikisol.arena.seed;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Demo logins take their password from the environment. The value is never logged.
 * Two previously published passwords are recognised only so an existing hash can be
 * replaced; they are not a login default.
 */
@Component
public class DemoPassword {

    private static final String[] RETIRED_PUBLIC_PASSWORDS = {"Demo@12345", "ArenaDemo2026!"};

    private final String configured;

    public DemoPassword(@Value("${ARENA_DEMO_PASSWORD:}") String configured) {
        this.configured = configured == null ? "" : configured;
    }

    public String required() {
        if (configured.isBlank()) {
            throw new IllegalStateException(
                    "ARENA_DEMO_PASSWORD must be set before demo accounts can be created. The value is never logged.");
        }
        return configured;
    }

    public Optional<String> configured() {
        return configured.isBlank() ? Optional.empty() : Optional.of(configured);
    }

    public boolean matchesRetired(PasswordEncoder encoder, String hash) {
        if (hash == null || hash.isBlank()) return false;
        for (String retired : RETIRED_PUBLIC_PASSWORDS) {
            if (encoder.matches(retired, hash)) return true;
        }
        return false;
    }
}
