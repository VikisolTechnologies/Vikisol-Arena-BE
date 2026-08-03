package com.vikisol.arena.integration.provider;

/**
 * Transactional-email abstraction - callers (AuthService, ApplicationService, InterviewService)
 * depend only on this interface, never on a concrete implementation directly. Which implementation
 * actually runs is resolved once at startup by
 * {@link com.vikisol.arena.integration.config.IntegrationProviderConfig}: the real provider
 * ({@link ResendEmailProvider}) if it's configured, otherwise {@link NoopEmailProvider}. Mirrors
 * the shape of HRLMS-BE's {@code MailProvider} (interface -> Noop -> real), independently
 * implemented for Arena.
 */
public interface EmailProvider {

    String getProviderName();

    boolean isConfigured();

    void sendEmail(EmailMessage message);
}
