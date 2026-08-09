package com.vikisol.arena.verification.service;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.entity.VerificationLevel;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.integration.provider.PhoneOtpProvider;
import com.vikisol.arena.verification.dto.VerificationStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * ARENA-V2-PRODUCT-ARCHITECTURE.md §4 verification tiers. Phone tier is fully real: a 6-digit
 * OTP is generated, hashed with the app's existing BCrypt PasswordEncoder (same mechanism as
 * password storage - no new hashing dependency), stored with a 10-minute expiry, and sent via
 * whichever PhoneOtpProvider is wired (NoopPhoneOtpProvider today - see BLOCKED.md/DECISIONS.md).
 * ID tier is intentionally not reachable from here yet.
 */
@Service
@RequiredArgsConstructor
public class VerificationService {

    private static final int OTP_LENGTH = 6;
    private static final long OTP_TTL_MINUTES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PhoneOtpProvider phoneOtpProvider;

    @Transactional(readOnly = true)
    public VerificationStatusResponse getStatus(UUID userId) {
        User user = requireUser(userId);
        boolean otpPending = user.getPendingOtpHash() != null
                && user.getPendingOtpExpiresAt() != null && user.getPendingOtpExpiresAt().isAfter(Instant.now());
        return new VerificationStatusResponse(user.getVerificationLevel().wireValue(), user.isPhoneVerified(), user.getPhoneNumber(), otpPending);
    }

    @Transactional
    public void requestPhoneOtp(UUID userId, String phoneNumber) {
        User user = requireUser(userId);
        String code = generateCode();
        user.setPhoneNumber(phoneNumber);
        user.setPendingOtpHash(passwordEncoder.encode(code));
        user.setPendingOtpExpiresAt(Instant.now().plus(OTP_TTL_MINUTES, ChronoUnit.MINUTES));
        userRepository.save(user);
        phoneOtpProvider.sendOtp(phoneNumber, code);
    }

    @Transactional
    public VerificationStatusResponse confirmPhoneOtp(UUID userId, String code) {
        User user = requireUser(userId);
        if (user.getPendingOtpHash() == null || user.getPendingOtpExpiresAt() == null) {
            throw new BadRequestException("No verification code is pending - request a new one");
        }
        if (user.getPendingOtpExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("That code has expired - request a new one");
        }
        if (!passwordEncoder.matches(code, user.getPendingOtpHash())) {
            throw new BadRequestException("That code doesn't match");
        }
        user.setPhoneVerified(true);
        user.setPendingOtpHash(null);
        user.setPendingOtpExpiresAt(null);
        if (!user.getVerificationLevel().atLeast(VerificationLevel.PHONE)) {
            user.setVerificationLevel(VerificationLevel.PHONE);
        }
        userRepository.save(user);
        return getStatus(userId);
    }

    @Transactional
    public void setDateOfBirth(UUID userId, LocalDate dateOfBirth) {
        if (dateOfBirth.isAfter(LocalDate.now())) {
            throw new BadRequestException("Date of birth can't be in the future");
        }
        User user = requireUser(userId);
        user.setDateOfBirth(dateOfBirth);
        userRepository.save(user);
    }

    private String generateCode() {
        int max = (int) Math.pow(10, OTP_LENGTH);
        return String.format("%0" + OTP_LENGTH + "d", RANDOM.nextInt(max));
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }
}
