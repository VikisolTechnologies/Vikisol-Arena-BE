package com.vikisol.arena.enterprise.service;

import com.vikisol.arena.business.service.BusinessVerificationService;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.enterprise.dto.EnterpriseProfileResponse;
import com.vikisol.arena.enterprise.dto.UpdateEnterpriseProfileRequest;
import com.vikisol.arena.enterprise.entity.CompanySize;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import com.vikisol.arena.enterprise.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EnterpriseProfileService {

    private final com.vikisol.arena.profile.industry.IndustryCatalogue industryCatalogue;
    private final EnterpriseProfileRepository enterpriseProfileRepository;
    private final MembershipRepository membershipRepository;
    private final EnterpriseProfileMapper mapper;
    private final com.vikisol.arena.common.service.FileStorageService fileStorageService;

    // Single source of truth for "which tenant does this enterprise-ish user belong to" -
    // resolves via Membership (recruiter/company_admin/hiring_manager all covered uniformly),
    // falling back to the legacy 1:1 EnterpriseProfile.user lookup as a safety net for any row
    // that somehow predates the Membership backfill (see seed.RoleMigration).
    @Transactional(readOnly = true)
    public EnterpriseProfile getEntityForUser(UUID userId) {
        return findEntityForUser(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No enterprise profile for this account"));
    }

    // A real, live bug this exists to fix (2026-09-15): ConversationService's two call sites
    // treat "not enterprise" as a normal, expected case (a candidate messaging another
    // candidate) and catch the ResourceNotFoundException getEntityForUser() throws - but that
    // method is its OWN @Transactional(readOnly = true) boundary, and Spring's default rollback
    // rule marks the whole PHYSICAL transaction (shared via propagation=REQUIRED, the default)
    // as rollback-only the instant the exception crosses that boundary - regardless of the
    // caller catching it immediately after. Every candidate-to-candidate direct message in this
    // product was silently failing to persist: sendMessage() ran to completion and returned a
    // normal-looking response, but the surrounding transaction could never actually commit.
    // Callers that genuinely need "not found" to be an error keep using getEntityForUser()
    // above (every other call site in this codebase does, correctly - the user there is
    // guaranteed enterprise already); callers doing a soft "is this user enterprise at all"
    // check should use this instead, which never throws.
    // Batched findEntityForUser() for a list of users (two IN-queries total) - same membership-
    // first, founding-admin-second resolution. Users with no tenant are simply absent.
    @Transactional(readOnly = true)
    public Map<UUID, EnterpriseProfile> mapByUserId(Collection<UUID> userIds) {
        if (userIds.isEmpty()) return Map.of();
        Map<UUID, EnterpriseProfile> out = new HashMap<>();
        membershipRepository.findByUserIdIn(userIds).forEach(m -> out.putIfAbsent(m.getUser().getId(), m.getTenant()));
        List<UUID> rest = userIds.stream().filter(id -> !out.containsKey(id)).distinct().toList();
        if (!rest.isEmpty()) {
            enterpriseProfileRepository.findByUserIdIn(rest).forEach(e -> out.putIfAbsent(e.getUser().getId(), e));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Optional<EnterpriseProfile> findEntityForUser(UUID userId) {
        return membershipRepository.findByUserId(userId)
                .map(m -> m.getTenant())
                .or(() -> enterpriseProfileRepository.findByUserId(userId));
    }

    @Transactional(readOnly = true)
    public EnterpriseProfileResponse getMyProfile(UUID userId) {
        return mapper.toResponse(getEntityForUser(userId));
    }

    @Transactional
    public EnterpriseProfileResponse updateMyProfile(UUID userId, UpdateEnterpriseProfileRequest request) {
        EnterpriseProfile profile = getEntityForUser(userId);
        profile.setCompanyName(request.companyName());
        profile.setLogoEmoji(request.logoEmoji());
        profile.setIndustry(industryCatalogue.resolveForWrite(request.industry(), profile.getIndustry()));
        profile.setSize(CompanySize.fromWireValue(request.size()));
        profile.setHiringFor(request.hiringFor());
        if (request.website() != null) {
            String w = request.website().trim();
            if (!w.isEmpty() && !w.matches("(https?://)?[A-Za-z0-9.-]+\\.[A-Za-z]{2,}(/\\S*)?")) throw new BadRequestException("That website doesn't look right");
            profile.setWebsite(w.isEmpty() ? null : w);
        }
        if (request.gstin() != null) profile.setGstin(BusinessVerificationService.companyId(request.gstin(), BusinessVerificationService.GSTIN, "GSTIN"));
        if (request.cin() != null) profile.setCin(BusinessVerificationService.companyId(request.cin(), BusinessVerificationService.CIN, "CIN"));
        if (request.hqCity() != null) profile.setHqCity(request.hqCity().isBlank() ? null : request.hqCity().trim());
        return mapper.toResponse(enterpriseProfileRepository.save(profile));
    }

    // Row 29: the company logo, an image uploaded like a profile photo. The old file is removed.
    @Transactional
    public EnterpriseProfileResponse uploadLogo(UUID userId, org.springframework.web.multipart.MultipartFile file) {
        String name = file == null ? null : file.getOriginalFilename();
        String extension = name != null && name.contains(".") ? name.substring(name.lastIndexOf('.')).toLowerCase(java.util.Locale.ROOT) : "";
        if (!java.util.Set.of(".png", ".jpg", ".jpeg", ".webp").contains(extension)) {
            throw new BadRequestException("A logo must be a PNG, JPG or WebP image");
        }
        EnterpriseProfile profile = getEntityForUser(userId);
        var stored = fileStorageService.store(file, "company-logo", profile.getId().toString(), "logo");
        if (profile.getLogoUrl() != null) fileStorageService.delete(profile.getLogoUrl());
        profile.setLogoUrl(stored.url());
        return mapper.toResponse(enterpriseProfileRepository.save(profile));
    }

    @Transactional
    public EnterpriseProfileResponse deleteLogo(UUID userId) {
        EnterpriseProfile profile = getEntityForUser(userId);
        if (profile.getLogoUrl() != null) fileStorageService.delete(profile.getLogoUrl());
        profile.setLogoUrl(null);
        return mapper.toResponse(enterpriseProfileRepository.save(profile));
    }
}
