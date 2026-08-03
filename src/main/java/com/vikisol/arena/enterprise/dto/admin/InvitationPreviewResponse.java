package com.vikisol.arena.enterprise.dto.admin;

// Shown on the (unauthenticated) accept-invite page before the invitee sets a password - lets
// them confirm which company/role they're joining without needing to sign in first.
public record InvitationPreviewResponse(
        String email,
        String role,
        String companyName,
        String companyLogoEmoji,
        boolean valid,
        String invalidReason
) {
}
