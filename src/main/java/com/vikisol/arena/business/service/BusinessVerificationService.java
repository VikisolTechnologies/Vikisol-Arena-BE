package com.vikisol.arena.business.service;

import com.vikisol.arena.audit.AuditActions;
import com.vikisol.arena.audit.AuditService;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.business.dto.BusinessDtos.*;
import com.vikisol.arena.business.entity.BusinessVerification;
import com.vikisol.arena.business.repository.BusinessVerificationRepository;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import com.vikisol.arena.enterprise.service.EnterpriseProfileService;
import com.vikisol.arena.integration.provider.EmailMessage;
import com.vikisol.arena.integration.provider.EmailProvider;
import com.vikisol.arena.integration.provider.ProviderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

// G27: a company proves it controls its website's domain. The work email must be at that domain
// (or a subdomain of it) and not a free mail provider; a 6-digit code goes there, and only the
// confirmed code turns the badge on. No document upload, no manual review: honest about what it
// proves (domain control) and nothing more.
@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessVerificationService {

    static final Duration CODE_TTL = Duration.ofMinutes(30);
    static final Duration RESEND_AFTER = Duration.ofSeconds(60);
    static final int MAX_ATTEMPTS = 5;

    // Personal mailboxes prove nothing about a company.
    static final Set<String> FREE_MAIL = Set.of("gmail.com", "googlemail.com", "yahoo.com", "yahoo.in", "yahoo.co.in",
            "outlook.com", "hotmail.com", "live.com", "msn.com", "icloud.com", "me.com", "aol.com", "proton.me",
            "protonmail.com", "rediffmail.com", "zohomail.com", "zohomail.in", "yandex.com", "gmx.com", "mail.com");

    private static final SecureRandom RANDOM = new SecureRandom();

    private final BusinessVerificationRepository repository;
    private final EnterpriseProfileService enterpriseProfileService;
    private final EnterpriseProfileRepository enterpriseProfileRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailProvider emailProvider;
    private final AuditService auditService;

    // Company admin. Submitting (again) always starts over: new details, new code, not verified.
    @Transactional
    public VerificationView submit(UUID adminId, SubmitRequest request) {
        EnterpriseProfile tenant = enterpriseProfileService.getEntityForUser(adminId);
        BusinessVerification.SubmitterRole role;
        try {
            role = BusinessVerification.SubmitterRole.valueOf(request.submitterRole().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("submitterRole must be one of founder, hr, talent_acquisition, hiring_manager, operations, other");
        }
        String domain = domainOf(request.website());
        String email = request.workEmail().trim().toLowerCase(Locale.ROOT);
        String emailDomain = email.substring(email.indexOf('@') + 1);
        if (FREE_MAIL.contains(emailDomain)) throw new BadRequestException("Use your work email at " + domain + ", not a personal mailbox");
        if (!emailDomain.equals(domain) && !emailDomain.endsWith("." + domain)) {
            throw new BadRequestException("The work email must be at " + domain + " to prove you control that website");
        }
        BusinessVerification v = repository.findByTenantId(tenant.getId())
                .orElseGet(() -> BusinessVerification.builder().tenant(tenant).build());
        if (v.getLastSentAt() != null && v.getLastSentAt().plus(RESEND_AFTER).isAfter(Instant.now())) {
            throw new BadRequestException("A code was just sent. Wait a minute before asking for another.");
        }
        v.setLegalName(request.legalName().trim());
        v.setWebsite(request.website().trim());
        v.setDomain(domain);
        v.setWorkEmail(email);
        v.setSubmitterRole(role);
        v.setStatus(BusinessVerification.Status.PENDING);
        v.setVerifiedAt(null);
        v.setVerifiedBy(null);
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        v.setCodeHash(passwordEncoder.encode(code));
        v.setCodeExpiresAt(Instant.now().plus(CODE_TTL));
        v.setAttempts(0);
        v.setLastSentAt(Instant.now());
        repository.save(v);
        try {
            emailProvider.sendEmail(EmailMessage.to(email, "Your Arena company verification code",
                    "<p>Use this code to verify " + escape(v.getLegalName()) + " on Vikisol Arena:</p>"
                            + "<p style=\"font-size:32px;font-weight:700;letter-spacing:6px;\">" + code + "</p>"
                            + "<p>It expires in 30 minutes. If you didn't ask for this, you can ignore this email.</p>"));
        } catch (ProviderException e) {
            throw new ProviderException(ProviderException.Kind.CODE);
        }
        return toView(v);
    }

    @Transactional
    public VerificationView confirm(UUID adminId, String code) {
        EnterpriseProfile tenant = enterpriseProfileService.getEntityForUser(adminId);
        BusinessVerification v = repository.findByTenantId(tenant.getId())
                .orElseThrow(() -> new BadRequestException("Start verification first"));
        if (v.getStatus() == BusinessVerification.Status.VERIFIED) return toView(v);
        if (v.getCodeHash() == null || v.getCodeExpiresAt() == null || v.getCodeExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("That code has expired. Ask for a new one.");
        }
        if (v.getAttempts() >= MAX_ATTEMPTS) throw new BadRequestException("Too many wrong codes. Ask for a new one.");
        if (!passwordEncoder.matches(code.trim(), v.getCodeHash())) {
            v.setAttempts(v.getAttempts() + 1);
            repository.save(v);
            throw new BadRequestException("That code isn't right");
        }
        v.setStatus(BusinessVerification.Status.VERIFIED);
        v.setVerifiedAt(Instant.now());
        v.setVerifiedBy(userRepository.getReferenceById(adminId));
        v.setCodeHash(null);
        v.setCodeExpiresAt(null);
        repository.save(v);
        auditService.record(tenant.getId(), adminId, AuditActions.BUSINESS_VERIFIED, v.getLegalName(), "domain: " + v.getDomain());
        return toView(v);
    }

    // Anyone on the team can read their own company's status.
    @Transactional(readOnly = true)
    public VerificationView mine(UUID memberId) {
        EnterpriseProfile tenant = enterpriseProfileService.getEntityForUser(memberId);
        return repository.findByTenantId(tenant.getId()).map(this::toView)
                .orElse(new VerificationView("not_started", null, null, null, null, null, null, null));
    }

    // Public badge on a company page.
    @Transactional(readOnly = true)
    public PublicBadge badge(UUID companyId) {
        enterpriseProfileRepository.findById(companyId).orElseThrow(() -> new ResourceNotFoundException("Company not found: " + companyId));
        return repository.findByTenantId(companyId)
                .filter(v -> v.getStatus() == BusinessVerification.Status.VERIFIED)
                .map(v -> new PublicBadge(true, v.getDomain(), v.getVerifiedAt().toString()))
                .orElse(new PublicBadge(false, null, null));
    }

    static String domainOf(String website) {
        String raw = website.trim().toLowerCase(Locale.ROOT);
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) raw = "https://" + raw;
        String host;
        try {
            host = URI.create(raw).getHost();
        } catch (IllegalArgumentException e) {
            host = null;
        }
        if (host == null || !host.contains(".")) throw new BadRequestException("That website doesn't look right");
        if (host.startsWith("www.")) host = host.substring(4);
        if (FREE_MAIL.contains(host)) throw new BadRequestException("Use your company's own website");
        return host;
    }

    private VerificationView toView(BusinessVerification v) {
        return new VerificationView(v.getStatus().name().toLowerCase(Locale.ROOT), v.getLegalName(), v.getWebsite(), v.getDomain(),
                mask(v.getWorkEmail()), v.getSubmitterRole().name().toLowerCase(Locale.ROOT),
                v.getStatus() == BusinessVerification.Status.PENDING && v.getCodeExpiresAt() != null ? v.getCodeExpiresAt().toString() : null,
                v.getVerifiedAt() == null ? null : v.getVerifiedAt().toString());
    }

    private static String mask(String email) {
        int at = email.indexOf('@');
        return (at <= 1 ? "*" : email.charAt(0) + "***") + email.substring(at);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
