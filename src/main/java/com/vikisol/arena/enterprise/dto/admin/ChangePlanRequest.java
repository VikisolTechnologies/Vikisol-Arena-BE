package com.vikisol.arena.enterprise.dto.admin;

import jakarta.validation.constraints.NotBlank;

public record ChangePlanRequest(@NotBlank(message = "is required") String plan) {
}
