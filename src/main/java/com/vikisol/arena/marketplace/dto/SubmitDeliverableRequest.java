package com.vikisol.arena.marketplace.dto;

import jakarta.validation.constraints.NotBlank;

public record SubmitDeliverableRequest(@NotBlank(message = "is required") String note, String fileUrl) {
}
