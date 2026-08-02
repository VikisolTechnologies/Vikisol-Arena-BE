package com.vikisol.arena.applications.dto;

import jakarta.validation.constraints.NotBlank;

public record ApplyRequest(@NotBlank(message = "is required") String jobId) {
}
