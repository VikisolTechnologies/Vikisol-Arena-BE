package com.vikisol.arena.marketplace.dto;

import jakarta.validation.constraints.NotBlank;

public record AwardRequest(@NotBlank(message = "is required") String bidId) {
}
