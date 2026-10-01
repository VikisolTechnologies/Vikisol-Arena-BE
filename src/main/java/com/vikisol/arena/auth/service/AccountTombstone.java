package com.vikisol.arena.auth.service;

import com.vikisol.arena.auth.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * ARCHITECT-REVIEW-BE-1 blocker #6: erasing an account (self-service or admin-triggered) set the
 * display name to "Deleted user" and {@code deletedAt}, but left the email, phone, handle and
 * password hash exactly as they were - the account's real identity survived "erasure" intact, and
 * (since {@link com.vikisol.arena.auth.service.AuthService#issueSession} only checked
 * {@code isBlocked()}, not {@code deletedAt}) the original credentials could still sign in.
 *
 * <p>One shared tombstone step, called by every erasure path, so none of them can drift out of
 * sync on what "erased" actually clears.
 */
@Component
@RequiredArgsConstructor
public class AccountTombstone {

    private final PasswordEncoder passwordEncoder;

    /** Mutates {@code user} in place - the caller still saves it. Does not touch {@code name} or
     * {@code deletedAt}; callers already set those themselves (the display name has to stay
     * "Deleted user", a name a real signup could collide with if generated here too). */
    public void tombstone(User user) {
        user.setEmail("erased-" + UUID.randomUUID() + "@erased.vikisol.in");
        user.setHandle(null);
        user.setPhoneNumber(null);
        user.setPhoneVerified(false);
        // A random, never-communicated password - not blank (passwordHash is NOT NULL) and not
        // derivable from anything the erased person could reconstruct.
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
    }
}
