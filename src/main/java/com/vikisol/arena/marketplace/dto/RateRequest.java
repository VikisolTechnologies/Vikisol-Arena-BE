package com.vikisol.arena.marketplace.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RateRequest(@NotNull(message = "is required") @Min(1) @Max(5) Integer score, String comment) {
}
