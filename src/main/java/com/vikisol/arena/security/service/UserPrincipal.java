package com.vikisol.arena.security.service;

import com.vikisol.arena.auth.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Getter
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final String name;
    private final com.vikisol.arena.auth.entity.Role role;
    // Rows 50-51: read when the principal is loaded (every request), so a suspension, ban,
    // erasure or force sign-out takes effect on the next request (JwtAuthenticationFilter).
    private final boolean blocked;
    private final java.time.Instant sessionsRevokedAt;

    public UserPrincipal(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.name = user.getName();
        this.role = user.getRole();
        this.blocked = user.getDeletedAt() != null || user.isBlocked(java.time.Instant.now());
        this.sessionsRevokedAt = user.getSessionsRevokedAt();
    }

    /**
     * False when the account is blocked, or the token was issued strictly before a force sign-out.
     *
     * <p>MARATHON-BE-2 step 1b item 5: an earlier pass made this strictly-after sessionsRevokedAt,
     * closing a same-second bypass where a token minted in the very same second as a force
     * sign-out read as "not before" it and slipped through. But sessionsRevokedAt and a JWT's
     * {@code iat} are both second-precision, and a flow that revokes sessions and mints a fresh
     * token for the same account in the same request (password change, "sign out other devices")
     * can legitimately produce a token whose iat equals sessionsRevokedAt to the second - strictly
     * after would reject that token too, locking the user out of the session they were just
     * handed. At-or-after is the right boundary: the only thing lost is a sub-second window where
     * a token minted the instant before revocation, in the same second, stays valid - a much
     * narrower and less practically exploitable gap than breaking same-request reissue outright.
     */
    public boolean acceptsTokenIssuedAt(java.util.Date issuedAt) {
        if (blocked) return false;
        return sessionsRevokedAt == null || issuedAt == null || !issuedAt.toInstant().isBefore(sessionsRevokedAt);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    // Lockout (checklist §1) is enforced explicitly in AuthService.signIn() BEFORE
    // authenticationManager.authenticate() is ever called, with its own user-facing message
    // ("try again in N minutes") - not wired through this UserDetails contract, since
    // DaoAuthenticationProvider would throw a generic LockedException here instead, losing that
    // message. Always true is correct for this class's actual role in the auth flow.
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
