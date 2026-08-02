package com.vikisol.arena.enterprise.dto;

import jakarta.validation.constraints.NotBlank;

public record SetPostingStatusRequest(@NotBlank(message = "is required") String status) {
}
