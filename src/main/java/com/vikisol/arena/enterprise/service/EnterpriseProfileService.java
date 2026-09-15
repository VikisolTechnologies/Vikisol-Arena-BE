package com.vikisol.arena.enterprise.service;

import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.enterprise.dto.EnterpriseProfileResponse;
import com.vikisol.arena.enterprise.dto.UpdateEnterpriseProfileRequest;
import com.vikisol.arena.enterprise.entity.CompanySize;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import com.vikisol.arena.enterprise.repository.MembershipRepository;
import com.vikisol.arena.profile.entity.Industry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EnterpriseProfileService {

    private final EnterpriseProfileRepository enterpriseProfileRepository;
    private final MembershipRepository membershipRepository;
    private final EnterpriseProfileMapper mapper;

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
        profile.setIndustry(Industry.fromWireValue(request.industry()));
        profile.setSize(CompanySize.fromWireValue(request.size()));
        profile.setHiringFor(request.hiringFor());
        return mapper.toResponse(enterpriseProfileRepository.save(profile));
    }
}
