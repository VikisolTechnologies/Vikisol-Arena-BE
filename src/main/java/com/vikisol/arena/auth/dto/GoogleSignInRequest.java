package com.vikisol.arena.auth.dto;

import jakarta.validation.constraints.NotBlank;

// The ID token Google Identity Services hands the frontend after a successful "Sign in with
// Google" - verified server-side (see GoogleIdTokenVerifier) before it's trusted for anything.
public record GoogleSignInRequest(
        @NotBlank(message = "is required") String idToken
) {
}
