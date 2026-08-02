package com.vikisol.arena.profile.dto;

import jakarta.validation.constraints.NotNull;

public record ConsentDto(
        @NotNull(message = "is required") Boolean autoApply,
        @NotNull(message = "is required") Boolean searchableByEnterprises
) {
}
