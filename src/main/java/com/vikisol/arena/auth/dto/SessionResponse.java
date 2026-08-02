package com.vikisol.arena.auth.dto;

// Mirrors arena-web's `Session` type exactly (role, candidateId, name, email), plus the JWT the
// frontend's future HTTP adapter will need to attach as an Authorization header.
public record SessionResponse(
        String role,
        String candidateId,
        String name,
        String email,
        String token
) {
}
