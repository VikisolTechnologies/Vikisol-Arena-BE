package com.vikisol.arena.posts.dto;

import jakarta.validation.constraints.NotBlank;

public record ReportPostRequest(@NotBlank(message = "is required") String reason) {
}
