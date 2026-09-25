package com.vikisol.arena.posts.dto;

import jakarta.validation.constraints.NotBlank;

// parentCommentId - Phase 2 (Discuss) threaded replies: the comment this one answers, or null.
// anonymous - Phase 2 part C: reply under a per-thread alias (Discuss threads only).
public record CreateCommentRequest(@NotBlank(message = "is required") String content, java.util.UUID parentCommentId, Boolean anonymous) {
}
