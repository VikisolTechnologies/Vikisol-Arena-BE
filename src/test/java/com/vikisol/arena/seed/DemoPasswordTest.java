package com.vikisol.arena.seed;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DemoPasswordTest {

    @Test
    void blankEnvironmentRefusesToCreateDemoAccounts() {
        DemoPassword passwords = new DemoPassword("");
        assertThrows(IllegalStateException.class, passwords::required);
    }

    @Test
    void aFreshHashIsNotTreatedAsRetired() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        DemoPassword passwords = new DemoPassword("");
        assertFalse(passwords.matchesRetired(encoder, encoder.encode("local-only-value")));
    }
}
