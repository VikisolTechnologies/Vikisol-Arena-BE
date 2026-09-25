package com.vikisol.arena.posts.dto;

import jakarta.validation.constraints.NotBlank;

// parentCommentId - Phase 2 (Discuss) threaded replies: the comment this one answers, or null.
public record CreateCommentRequest(@NotBlank(message = "is required") String content, java.util.UUID parentCommentId) {
}
