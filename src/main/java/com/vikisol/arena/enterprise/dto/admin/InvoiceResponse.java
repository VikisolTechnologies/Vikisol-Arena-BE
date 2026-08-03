package com.vikisol.arena.enterprise.dto.admin;

// Mock invoice history - no real payment provider integrated (matches the rest of the app's
// mocked-payments posture, e.g. arena-web's plan.ts). Generated deterministically from the
// tenant's creation date + current plan, not stored, so there's nothing to keep in sync.
public record InvoiceResponse(
        String id,
        String date,
        String amount,
        String status
) {
}
