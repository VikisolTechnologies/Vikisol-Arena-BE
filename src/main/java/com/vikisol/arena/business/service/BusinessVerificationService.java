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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final com.vikisol.arena.notifications.service.NotificationService notificationService;

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
        String gstin = companyId(request.gstin(), GSTIN, "GSTIN");
        String cin = companyId(request.cin(), CIN, "CIN");
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
        v.setDomainConfirmedAt(null);
        v.setReviewNote(null);
        v.setReviewedBy(null);
        v.setReviewedAt(null);
        tenant.setWebsite(request.website().trim());
        if (gstin != null) tenant.setGstin(gstin);
        if (cin != null) tenant.setCin(cin);
        if (request.hqCity() != null && !request.hqCity().isBlank()) tenant.setHqCity(request.hqCity().trim());
        enterpriseProfileRepository.save(tenant);
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
        // Flow §8 B2: the code proves the domain; an Arena admin then reviews the request.
        v.setDomainConfirmedAt(Instant.now());
        v.setCodeHash(null);
        v.setCodeExpiresAt(null);
        repository.save(v);
        auditService.record(tenant.getId(), adminId, AuditActions.BUSINESS_DOMAIN_CONFIRMED, v.getLegalName(), "domain: " + v.getDomain());
        return toView(v);
    }

    // --- flow §9: the Arena admin verification queue ---------------------------------------

    // Default: waiting for review (domain confirmed, not decided). status=verified|rejected
    // lists decided ones, newest review first.
    @Transactional(readOnly = true)
    public Page<QueueItem> queue(String status, Pageable pageable) {
        Page<BusinessVerification> page;
        if (status == null || status.isBlank() || status.equalsIgnoreCase("pending")) {
            page = repository.findByStatusAndDomainConfirmedAtIsNotNullOrderByDomainConfirmedAtAscIdAsc(BusinessVerification.Status.PENDING, pageable);
        } else {
            BusinessVerification.Status s;
            try {
                s = BusinessVerification.Status.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("status must be one of pending, verified, rejected");
            }
            page = repository.findByStatusOrderByReviewedAtDescIdDesc(s, pageable);
        }
        return page.map(this::toQueueItem);
    }

    @Transactional
    public QueueItem approve(UUID platformAdminId, UUID verificationId) {
        BusinessVerification v = requireVerification(verificationId);
        if (v.getDomainConfirmedAt() == null) throw new BadRequestException("The company hasn't confirmed its work-email code yet");
        v.setStatus(BusinessVerification.Status.VERIFIED);
        v.setVerifiedAt(Instant.now());
        v.setVerifiedBy(userRepository.getReferenceById(platformAdminId));
        v.setReviewNote(null);
        v.setReviewedBy(userRepository.getReferenceById(platformAdminId));
        v.setReviewedAt(Instant.now());
        repository.save(v);
        auditService.record(v.getTenant().getId(), platformAdminId, AuditActions.BUSINESS_VERIFIED, v.getLegalName(), "domain: " + v.getDomain());
        notificationService.notifyJob(v.getTenant().getUser(), "Your company is verified",
                v.getLegalName() + " now shows the verified badge.");
        return toQueueItem(v);
    }

    @Transactional
    public QueueItem reject(UUID platformAdminId, UUID verificationId, String note) {
        BusinessVerification v = requireVerification(verificationId);
        v.setStatus(BusinessVerification.Status.REJECTED);
        v.setVerifiedAt(null);
        v.setVerifiedBy(null);
        v.setReviewNote(note.trim());
        v.setReviewedBy(userRepository.getReferenceById(platformAdminId));
        v.setReviewedAt(Instant.now());
        repository.save(v);
        auditService.record(v.getTenant().getId(), platformAdminId, AuditActions.BUSINESS_REJECTED, v.getLegalName(), note.trim());
        notificationService.notifyJob(v.getTenant().getUser(), "Company verification wasn't approved",
                "Arena's team said: " + note.trim() + " You can update the details and submit again.");
        return toQueueItem(v);
    }

    @Transactional(readOnly = true)
    public boolean isVerified(UUID tenantId) {
        return repository.findByTenantId(tenantId).map(v -> v.getStatus() == BusinessVerification.Status.VERIFIED).orElse(false);
    }

    private BusinessVerification requireVerification(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Verification request not found: " + id));
    }

    private QueueItem toQueueItem(BusinessVerification v) {
        EnterpriseProfile t = v.getTenant();
        return new QueueItem(v.getId().toString(), t.getId().toString(), t.getCompanyName(), v.getLegalName(), v.getWebsite(),
                v.getDomain(), v.getWorkEmail(), v.getSubmitterRole().name().toLowerCase(Locale.ROOT), t.getGstin(), t.getCin(),
                t.getHqCity(), v.getStatus().name().toLowerCase(Locale.ROOT),
                v.getDomainConfirmedAt() == null ? null : v.getDomainConfirmedAt().toString(), v.getReviewNote(),
                v.getReviewedAt() == null ? null : v.getReviewedAt().toString());
    }

    // GSTIN: 15 characters (state code, PAN, entity, Z, check). CIN: 21 characters.
    public static final java.util.regex.Pattern GSTIN = java.util.regex.Pattern.compile("[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]");
    public static final java.util.regex.Pattern CIN = java.util.regex.Pattern.compile("[LU][0-9]{5}[A-Z]{2}[0-9]{4}[A-Z]{3}[0-9]{6}");

    public static String companyId(String value, java.util.regex.Pattern pattern, String label) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim().toUpperCase(Locale.ROOT);
        if (!pattern.matcher(v).matches()) throw new BadRequestException("That " + label + " doesn't look right");
        return v;
    }

    // Anyone on the team can read their own company's status.
    @Transactional(readOnly = true)
    public VerificationView mine(UUID memberId) {
        EnterpriseProfile tenant = enterpriseProfileService.getEntityForUser(memberId);
        return repository.findByTenantId(tenant.getId()).map(this::toView)
                .orElse(new VerificationView("none", null, null, null, null, null, null, null, false, null));
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
                v.getVerifiedAt() == null ? null : v.getVerifiedAt().toString(),
                v.getDomainConfirmedAt() != null, v.getReviewNote());
    }

    private static String mask(String email) {
        int at = email.indexOf('@');
        return (at <= 1 ? "*" : email.charAt(0) + "***") + email.substring(at);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
