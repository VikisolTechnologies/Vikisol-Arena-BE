package com.vikisol.arena.marketplace.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PlaceBidRequest(@NotNull(message = "is required") @Positive(message = "must be positive") Integer amount) {
}
