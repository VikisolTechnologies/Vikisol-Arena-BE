package com.vikisol.arena.enterprise.dto.admin;

public record TeamMemberResponse(
        String membershipId,
        String userId,
        String name,
        String email,
        String role,
        String status,
        String invitedByName,
        String joinedAt
) {
}
