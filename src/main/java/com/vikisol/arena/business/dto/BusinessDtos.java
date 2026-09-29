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
            @NotBlank(message = "is required") String submitterRole
    ) {
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
            String verifiedAt
    ) {
    }

    // What anyone can see on a company: a badge, and the domain it was proven for.
    public record PublicBadge(boolean verified, String domain, String verifiedAt) {
    }
}
