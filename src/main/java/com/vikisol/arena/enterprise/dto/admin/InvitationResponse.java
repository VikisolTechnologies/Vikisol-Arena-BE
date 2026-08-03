package com.vikisol.arena.enterprise.dto.admin;

public record InvitationResponse(
        String id,
        String email,
        String role,
        // The full accept-link, not just the raw token - no email provider is configured, so the
        // admin UI surfaces this directly for the demo (see Invitation.java's own note).
        String inviteLink,
        String status,
        String expiresAt,
        String invitedByName,
        String createdAt
) {
}
