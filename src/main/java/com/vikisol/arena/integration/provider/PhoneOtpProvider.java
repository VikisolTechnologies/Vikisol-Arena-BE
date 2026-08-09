package com.vikisol.arena.integration.provider;

/**
 * SMS-OTP abstraction, same interface -> Noop -> real shape as {@link EmailProvider}/
 * {@link WhatsAppProvider}. Only {@link NoopPhoneOtpProvider} exists today (no real SMS vendor
 * wired - see BLOCKED.md); {@link com.vikisol.arena.verification.service.VerificationService}
 * depends only on this interface, so a real Twilio-or-similar implementation later is a pure
 * drop-in that only needs a new {@code @Bean @Primary} factory in
 * {@link com.vikisol.arena.integration.config.IntegrationProviderConfig}, exactly like
 * {@code ResendEmailProvider}/{@code WhatsAppBusinessProvider} were added.
 */
public interface PhoneOtpProvider {

    String getProviderName();

    boolean isConfigured();

    void sendOtp(String phoneNumber, String code);
}
