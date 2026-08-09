package com.vikisol.arena.posts.dto;

// Field-for-field mirror of arena-web's PostComment type.
public record PostCommentResponse(
        String id,
        String postId,
        String authorUserId,
        String authorName,
        String authorEmoji,
        String content,
        String createdAt
) {
}
