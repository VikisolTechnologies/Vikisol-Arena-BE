package com.vikisol.arena.marketplace.dto;

// Mirrors arena-web's `Deliverable{note, submittedAt}` shape exactly.
public record DeliverableResponse(
        String note,
        String submittedAt
) {
}
