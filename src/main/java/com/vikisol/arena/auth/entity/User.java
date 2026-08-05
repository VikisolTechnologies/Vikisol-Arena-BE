package com.vikisol.arena.auth.entity;

import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

// Table prefixed "arena_" - see README decisions: this local Postgres database already contained
// unrelated tables (including one literally named "users") from a prior, unrelated project.
// Namespacing avoids any collision without needing a destructive DB reset.
@Entity
@Table(name = "arena_users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // 2FA (TOTP) - see DECISIONS.md and TotpService. totpSecret is set once at /auth/2fa/setup
    // and only takes effect once totpEnabled flips true via /auth/2fa/enable (a code-verified
    // confirmation step) - a bare setup call alone never gates sign-in. Base32 secret stored in
    // plaintext in this column is a known simplification (real production would encrypt it at
    // rest); flagged as a fast-follow in DECISIONS.md, not silently accepted.
    private String totpSecret;

    // columnDefinition default needed so ddl-auto's ALTER TABLE ADD COLUMN NOT NULL succeeds
    // against this table's pre-existing rows (same pattern as EnterpriseProfile.status).
    @Column(nullable = false, columnDefinition = "boolean not null default false")
    @Builder.Default
    private boolean totpEnabled = false;

    // Login lockout/backoff (checklist §1) - incremented on each bad password, reset on success.
    // lockedUntil null/past = not locked.
    @Column(nullable = false, columnDefinition = "integer not null default 0")
    @Builder.Default
    private int failedLoginAttempts = 0;

    private Instant lockedUntil;
}
