package com.vikisol.arena.posts.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCommentRequest(@NotBlank(message = "is required") String content) {
}
