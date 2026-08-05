package com.vikisol.arena.auth.dto;

// Mirrors arena-web's `Session` type (role, candidateId, name, email), plus the JWT access token
// the frontend's HTTP adapter attaches as an Authorization header. mfaRequired/mfaPendingToken
// are only set when a password check succeeded but a TOTP code is still needed (see
// DECISIONS.md's 2FA flow) - every other field is null in that case, and no session/refresh
// cookie exists yet until POST /auth/2fa/verify succeeds.
public record SessionResponse(
        String role,
        String candidateId,
        String name,
        String email,
        String token,
        boolean mfaRequired,
        String mfaPendingToken
) {
    public static SessionResponse of(String role, String candidateId, String name, String email, String token) {
        return new SessionResponse(role, candidateId, name, email, token, false, null);
    }

    public static SessionResponse mfaRequired(String pendingToken) {
        return new SessionResponse(null, null, null, null, null, true, pendingToken);
    }
}
