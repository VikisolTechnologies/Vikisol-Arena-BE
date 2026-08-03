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
        log.info("[email:noop] would send \"{}\" to {}", message.subject(), message.to());
    }
}
