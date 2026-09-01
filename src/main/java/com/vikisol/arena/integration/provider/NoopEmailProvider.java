package com.vikisol.arena.integration.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// Default EmailProvider until RESEND_API_KEY (or a future provider) is configured - sends nothing
// for real, just logs what would have gone out, so every welcome/stage-change/interview-confirmed
// call site can fire unconditionally today without needing an "is email even set up" check at each
// call site. This is what makes the integration layer non-breaking: nothing regresses, and nothing
// requires real credentials, for local dev or an early deployment.
@Slf4j
@Component
public class NoopEmailProvider implements EmailProvider {

    @Override
    public String getProviderName() {
        return "None (log only)";
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public void sendEmail(EmailMessage message) {
        // Full body logged (not just subject/recipient) so flows like password-reset are
        // actually testable end-to-end without a real vendor - same reasoning
        // NoopPhoneOtpProvider already logs its full message, not just "sent a code."
        log.info("[email:noop] would send \"{}\" to {}:\n{}", message.subject(), message.to(), message.htmlBody());
    }
}
