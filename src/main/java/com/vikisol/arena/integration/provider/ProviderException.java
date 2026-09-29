package com.vikisol.arena.integration.provider;

import org.slf4j.Logger;

// An external provider (email, SMS, WhatsApp, Teams, OpenAI) failed. getMessage() is ALWAYS the
// short user-facing text for the kind of failure - never the provider's response - so it is safe
// wherever it ends up (GlobalExceptionHandler returns it with a 503). The provider's own detail
// is logged once, server-side and redacted, by failure().
public class ProviderException extends RuntimeException {

    public enum Kind {
        CODE("We couldn't send the code right now. Please try again in a minute."),
        EMAIL("We couldn't send the email right now. Please try again in a minute."),
        WHATSAPP("We couldn't send the WhatsApp message right now. Please try again in a minute."),
        MEETING_LINK("We couldn't create the meeting link right now. Please try again in a minute."),
        EMBEDDING("This is unavailable right now. Please try again in a minute.");

        private final String userMessage;

        Kind(String userMessage) {
            this.userMessage = userMessage;
        }

        public String userMessage() {
            return userMessage;
        }
    }

    private final Kind kind;

    public ProviderException(Kind kind) {
        super(kind.userMessage());
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    // Logs "<provider> <kind> failed: <redacted detail>" and returns the exception to throw.
    // The cause is not attached: its message is the unredacted provider text.
    public static ProviderException failure(Logger log, Kind kind, String provider, String detail) {
        log.error("{} {} failed: {}", provider, kind, ProviderLogs.redact(detail));
        return new ProviderException(kind);
    }

    public static ProviderException failure(Logger log, Kind kind, String provider, int status, String body) {
        return failure(log, kind, provider, "HTTP " + status + " " + body);
    }

    public static ProviderException failure(Logger log, Kind kind, String provider, Exception e) {
        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        return failure(log, kind, provider, e.getClass().getSimpleName() + ": " + e.getMessage());
    }
}
