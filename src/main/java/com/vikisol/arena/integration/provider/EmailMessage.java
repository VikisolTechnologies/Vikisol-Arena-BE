package com.vikisol.arena.integration.provider;

import java.util.List;

/**
 * Provider-agnostic email payload - built by callers (AuthService, ApplicationService,
 * InterviewService) and handed to whichever EmailProvider is currently active. Deliberately
 * minimal (no attachments/cc, unlike HRLMS-BE's MailMessage) since nothing in Arena today needs
 * more than a single HTML body to a recipient list - can grow the same way MailMessage did if a
 * future flow (e.g. attaching a generated offer letter) needs it.
 */
public record EmailMessage(
        List<String> to,
        String subject,
        String htmlBody
) {
    /** Convenience for the common case of a single recipient. */
    public static EmailMessage to(String recipient, String subject, String htmlBody) {
        return new EmailMessage(List.of(recipient), subject, htmlBody);
    }
}
