package com.vikisol.arena.posts.dto;

// Field-for-field mirror of arena-web's PostComment type.
public record PostCommentResponse(
        String id,
        String postId,
        String authorUserId,
        String authorName,
        String authorEmoji,
        String content,
        String createdAt,
        // Phase 2 (Discuss) threads: the comment this answers (null = top-level), and whether
        // it was deleted while still having replies (content is blank then).
        String parentCommentId,
        boolean deleted
) {
}
