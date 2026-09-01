package com.vikisol.arena.integration.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// Default (and today, only) PhoneOtpProvider - logs the code instead of SMS-ing it, same
// "nothing regresses, no real credentials required" reasoning as NoopEmailProvider/
// NoopWhatsAppProvider. The rest of the OTP flow (generate, hash, expire, confirm) is fully
// real regardless of which provider is wired underneath - see VerificationService.
@Slf4j
@Component
public class NoopPhoneOtpProvider implements PhoneOtpProvider {

    @Override
    public String getProviderName() {
        return "None (log only)";
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public void sendOtp(String phoneNumber, String code) {
        log.info("[otp:noop] would send to {}:\n{}", phoneNumber, buildOtpMessage(code));
    }
}
