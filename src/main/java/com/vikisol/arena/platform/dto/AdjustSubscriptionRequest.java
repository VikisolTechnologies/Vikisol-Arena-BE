package com.vikisol.arena.platform.dto;

import jakarta.validation.constraints.NotBlank;

public record AdjustSubscriptionRequest(
        String plan,
        Integer seatsTotal,
        Integer creditDelta,
        @NotBlank(message = "is required") String reason
) {
}
