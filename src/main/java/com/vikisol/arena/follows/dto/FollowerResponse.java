package com.vikisol.arena.follows.dto;

public record FollowerResponse(
        String userId,
        String name,
        String emoji,
        String followedAt
) {
}
