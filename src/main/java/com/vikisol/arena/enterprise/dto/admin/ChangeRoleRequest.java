package com.vikisol.arena.enterprise.dto.admin;

import jakarta.validation.constraints.NotBlank;

public record ChangeRoleRequest(@NotBlank(message = "is required") String role) {
}
