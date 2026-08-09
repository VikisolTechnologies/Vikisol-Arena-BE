package com.vikisol.arena.follows.dto;

public record BlockedUserResponse(
        String userId,
        String name,
        String emoji,
        String blockedAt
) {
}
