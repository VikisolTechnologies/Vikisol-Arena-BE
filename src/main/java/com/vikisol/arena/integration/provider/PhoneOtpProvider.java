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

    // WebOTP API (https://web.dev/web-otp/) lets Chrome on Android auto-read this exact SMS and
    // fill the code in without the user copy-pasting anything - but ONLY if the message's last
    // line is exactly "@<domain> #<code>" (no scheme, no path) and the frontend calls
    // navigator.credentials.get({otp:{transport:['sms']}}) - see PhoneAuthForm.tsx. A real SMS
    // provider (Twilio or similar) should build its message body via this method rather than
    // re-deriving the WebOTP format itself - one place to get the domain-bound suffix right.
    default String buildOtpMessage(String code) {
        return "Your Vikisol Arena verification code is " + code + ".\n@arena.vikisol.in #" + code;
    }
}
