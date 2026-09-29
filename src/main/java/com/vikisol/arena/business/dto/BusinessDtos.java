package com.vikisol.arena.business.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Bodies for /enterprise/verification and /enterprise/team/roles (G27, G28).
public final class BusinessDtos {

    private BusinessDtos() {
    }

    public record SubmitRequest(
            @NotBlank(message = "is required") @Size(max = 200, message = "must be at most 200 characters") String legalName,
            @NotBlank(message = "is required") @Size(max = 255, message = "must be at most 255 characters") String website,
            @NotBlank(message = "is required") @Email(message = "must be a valid email") String workEmail,
            @NotBlank(message = "is required") String submitterRole,
            // Row 29 (optional): checked by the Arena admin who reviews the request.
            String gstin,
            String cin,
            @Size(max = 60, message = "must be at most 60 characters") String hqCity
    ) {
        public SubmitRequest(String legalName, String website, String workEmail, String submitterRole) {
            this(legalName, website, workEmail, submitterRole, null, null, null);
        }
    }

    public record ConfirmRequest(@NotBlank(message = "is required") String code) {
    }

    // The team's own view. The work email is shown masked.
    public record VerificationView(
            String status,
            String legalName,
            String website,
            String domain,
            String workEmail,
            String submitterRole,
            String codeExpiresAt,
            String verifiedAt,
            // Row 29 (added): the code was confirmed (waiting for the admin), and the admin's
            // reason on a rejection.
            boolean domainConfirmed,
            String reviewNote
    ) {
    }

    // The admin verification queue (flow §9).
    public record QueueItem(
            String id,
            String companyId,
            String companyName,
            String legalName,
            String website,
            String domain,
            String workEmail,
            String submitterRole,
            String gstin,
            String cin,
            String hqCity,
            String status,
            String domainConfirmedAt,
            String reviewNote,
            String reviewedAt
    ) {
    }

    public record RejectRequest(@NotBlank(message = "is required") @Size(max = 500, message = "must be at most 500 characters") String note) {
    }

    // What anyone can see on a company: a badge, and the domain it was proven for.
    public record PublicBadge(boolean verified, String domain, String verifiedAt) {
    }
}
