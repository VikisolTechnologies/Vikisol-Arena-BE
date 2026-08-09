package com.vikisol.arena.posts.dto;

public record PostJoinRequestResponse(
        String id,
        String postId,
        String userId,
        String userName,
        String userEmoji,
        String status,
        String createdAt
) {
}
