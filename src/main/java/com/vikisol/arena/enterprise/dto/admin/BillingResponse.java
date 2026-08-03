package com.vikisol.arena.enterprise.dto.admin;

import java.util.List;

public record BillingResponse(
        String plan,
        int seatsUsed,
        int seatsTotal,
        int creditsUsed,
        int creditsTotal,
        List<InvoiceResponse> invoices
) {
}
