package com.vikisol.arena.enterprise.service;

import com.vikisol.arena.applications.repository.ApplicationRepository;
import com.vikisol.arena.audit.AuditActions;
import com.vikisol.arena.audit.AuditService;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.enterprise.dto.TalentSearchResult;
import com.vikisol.arena.enterprise.dto.admin.ConsentEntryResponse;
import com.vikisol.arena.enterprise.entity.CreditLedgerEntry;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.entity.UnlockedCandidate;
import com.vikisol.arena.enterprise.repository.CreditLedgerRepository;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import com.vikisol.arena.enterprise.repository.UnlockedCandidateRepository;
import com.vikisol.arena.matching.ScoringService;
import com.vikisol.arena.profile.dto.CandidateProfileResponse;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.entity.Industry;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import com.vikisol.arena.profile.service.CandidateProfileMapper;
import com.vikisol.arena.seed.IndianData;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TalentSearchService {

    private static final List<String> FIT_BLURBS = List.of(
            "Strong overlap with what you're hiring for, verified skills to back it up.",
            "Comes up frequently in searches like this one - high signal, low noise.",
            "A slightly non-obvious pick, but the skill graph lines up well.",
            "Recently active, open to new roles, and priced within typical range.");

    private final CandidateProfileRepository candidateProfileRepository;
    private final EnterpriseProfileRepository enterpriseProfileRepository;
    private final EnterpriseProfileService enterpriseProfileService;
    private final UnlockedCandidateRepository unlockedCandidateRepository;
    private final CreditLedgerRepository creditLedgerRepository;
    private final AuditService auditService;
    private final UserRepository userRepository;
    private final CandidateProfileMapper candidateProfileMapper;
    private final ScoringService scoringService;
    private final ApplicationRepository applicationRepository;

    @Transactional(readOnly = true)
    public PagedResponse<TalentSearchResult> search(UUID enterpriseUserId, String text, String industry, boolean remoteOnly, Pageable pageable) {
        EnterpriseProfile enterprise = requireEnterprise(enterpriseUserId);
        Industry industryEnum = (industry == null || industry.isBlank() || "All".equalsIgnoreCase(industry))
                ? null : Industry.fromWireValue(industry);
        // Always a non-null string ("" means "no filter") - the repository query relies on this,
        // see the comment on CandidateProfileRepository.search().
        String normalizedText = (text == null || text.isBlank()) ? "" : text.toLowerCase();

        var page = candidateProfileRepository.search(normalizedText, industryEnum, remoteOnly, pageable);
        List<UUID> candidateIds = page.getContent().stream().map(CandidateProfile::getId).toList();
        // Warm the skills/openTo @ElementCollections for the whole page in two queries total
        // (instead of one lazy load per collection per row) - see the repository comment for why
        // this isn't a single @EntityGraph on search() itself.
        batchFetchSkillsAndOpenTo(candidateIds);
        Set<UUID> unlockedIds = batchUnlockedCandidateIds(enterprise.getId(), candidateIds);
        Set<UUID> appliedIds = batchAppliedCandidateIds(enterprise.getId(), candidateIds);

        return PagedResponse.of(page, c -> toResult(c, unlockedIds, appliedIds));
    }

    private void batchFetchSkillsAndOpenTo(List<UUID> candidateIds) {
        if (candidateIds.isEmpty()) return;
        candidateProfileRepository.findByIdInFetchingSkills(candidateIds);
        candidateProfileRepository.findByIdInFetchingOpenTo(candidateIds);
    }

    // One query for every candidate's unlock state across a page (instead of one query per
    // candidate) - both toResult()'s own "unlocked" flag and redactIfLocked()'s access check need
    // this, so a single batched Set backs both instead of two separate per-row exists() calls.
    private Set<UUID> batchUnlockedCandidateIds(UUID enterpriseId, List<UUID> candidateIds) {
        if (candidateIds.isEmpty()) return Set.of();
        return new HashSet<>(unlockedCandidateRepository.findUnlockedCandidateIds(enterpriseId, candidateIds));
    }

    // One query for every candidate's "has this candidate applied to one of our postings" state
    // across a page (instead of one query per candidate) - feeds redactIfLocked()'s free-access
    // check.
    private Set<UUID> batchAppliedCandidateIds(UUID enterpriseId, List<UUID> candidateIds) {
        if (candidateIds.isEmpty()) return Set.of();
        return new HashSet<>(applicationRepository.findCandidateIdsWithApplicationToEnterprise(candidateIds, enterpriseId));
    }

    // Business-logic IDOR fix (found via the ARENA-SHIP-IT.md endpoint audit): this previously
    // returned the full profile - including cvUrl/cvFileName, the actual paid asset - to any
    // recruiter/company_admin regardless of unlock state, completely bypassing the credit
    // paywall search() results already respect client-side. Full access is granted for free
    // when the candidate directly applied to one of the caller's own postings (matches the
    // documented "direct applicants are visible for free" model - see enterprise.ts's
    // hasDirectlyApplied() comment on the frontend, previously mock-only, now real here too).
    @Transactional(readOnly = true)
    public CandidateProfileResponse getCandidateDetail(UUID enterpriseUserId, UUID id) {
        CandidateProfile candidate = candidateProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found: " + id));
        EnterpriseProfile enterprise = requireEnterprise(enterpriseUserId);
        return redactIfLocked(candidateProfileMapper.toResponse(candidate), enterprise, candidate.getId());
    }

    @Transactional
    public void unlock(UUID enterpriseUserId, UUID candidateId) {
        EnterpriseProfile enterpriseRef = requireEnterprise(enterpriseUserId);
        // Re-fetched under a row lock (held for the rest of this transaction) before either
        // check below - closes both the same-candidate double-click race and the cross-candidate
        // credit-balance race, since a second concurrent unlock() for this tenant now blocks here
        // until the first transaction commits, then sees its up-to-date state.
        EnterpriseProfile enterprise = enterpriseProfileRepository.findByIdForUpdate(enterpriseRef.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Enterprise profile not found"));
        if (unlockedCandidateRepository.existsByEnterpriseIdAndCandidateId(enterprise.getId(), candidateId)) {
            return; // already unlocked - idempotent
        }
        if (enterprise.getUnlockCreditsUsed() >= enterprise.getUnlockCreditsTotal()) {
            throw new BadRequestException("You're out of unlock credits on the " + enterprise.getPlan().wireValue()
                    + " plan. Upgrade your plan to unlock more candidate profiles.");
        }
        CandidateProfile candidate = candidateProfileRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found: " + candidateId));

        unlockedCandidateRepository.save(UnlockedCandidate.builder().enterprise(enterprise).candidate(candidate).build());
        enterprise.setUnlockCreditsUsed(enterprise.getUnlockCreditsUsed() + 1);
        enterpriseProfileRepository.save(enterprise);

        int balanceAfter = enterprise.getUnlockCreditsTotal() - enterprise.getUnlockCreditsUsed();
        creditLedgerRepository.save(CreditLedgerEntry.builder()
                .tenant(enterprise).actor(userRepository.getReferenceById(enterpriseUserId)).delta(-1)
                .reason("Unlocked " + candidate.getName()).balanceAfter(balanceAfter).build());
        auditService.record(enterprise.getId(), enterpriseUserId, AuditActions.CANDIDATE_UNLOCKED, candidate.getName());
        auditService.record(enterprise.getId(), enterpriseUserId, AuditActions.CREDIT_SPENT,
                "1 credit for " + candidate.getName(), "balance: " + balanceAfter);
    }

    // CA6 (consent & compliance view): every candidate this tenant has unlocked, with their
    // *current* consent state - not a snapshot from unlock time, so a withdrawal shows up here
    // immediately (G8's own requirement).
    @Transactional(readOnly = true)
    public List<ConsentEntryResponse> getConsentView(UUID enterpriseUserId) {
        EnterpriseProfile enterprise = requireEnterprise(enterpriseUserId);
        return unlockedCandidateRepository.findByEnterpriseId(enterprise.getId()).stream()
                .map(u -> new ConsentEntryResponse(
                        u.getCandidate().getId().toString(), u.getCandidate().getName(), u.getCreatedAt().toString(),
                        u.getCandidate().getConsent().isSearchableByEnterprises()))
                .toList();
    }

    // Batched call site (search) - unlocked/applied state precomputed for the whole page, see
    // batchUnlockedCandidateIds/batchAppliedCandidateIds.
    private TalentSearchResult toResult(CandidateProfile candidate, Set<UUID> unlockedIds, Set<UUID> appliedIds) {
        boolean unlocked = unlockedIds.contains(candidate.getId());
        int matchPercentage = scoringService.computeMatchPercentage(candidate, Set.of(), null);
        boolean fullAccess = unlocked || appliedIds.contains(candidate.getId());
        return new TalentSearchResult(
                redactIfLocked(candidateProfileMapper.toResponse(candidate), fullAccess), matchPercentage,
                IndianData.pick(FIT_BLURBS), String.join(", ", candidate.getOpenTo().stream().map(o -> o.wireValue()).toList()),
                unlocked);
    }

    // Single-item call site (getCandidateDetail) - two queries, fine for a one-off lookup outside
    // the paginated search hot path.
    private CandidateProfileResponse redactIfLocked(CandidateProfileResponse response, EnterpriseProfile enterprise, UUID candidateId) {
        boolean fullAccess = unlockedCandidateRepository.existsByEnterpriseIdAndCandidateId(enterprise.getId(), candidateId)
                || applicationRepository.existsByCandidateIdAndJobPostingEnterpriseId(candidateId, enterprise.getId());
        return redactIfLocked(response, fullAccess);
    }

    // The only actually-paywalled field: the CV file link. Everything else (skills, title,
    // location, career health) is meant to be visible pre-unlock so a recruiter can decide
    // whether a candidate is worth a credit at all - only the resume itself is gated.
    //
    // Phase B geo fields (homeCity/approxLat/approxLng) are ALWAYS stripped here, regardless of
    // unlock status - ARENA-V2-PRODUCT-ARCHITECTURE.md §5's location consent is scoped to
    // peer-to-peer activity discovery (Feed/Map), never to enterprise recruiter search, and no
    // unlock-credit "pays for" a candidate's approximate home location. locationConsent (just
    // the tier label, e.g. "off"/"city"/"precise") is harmless to leave visible on its own.
    private CandidateProfileResponse redactIfLocked(CandidateProfileResponse response, boolean fullAccess) {
        return new CandidateProfileResponse(
                response.id(), response.name(), response.avatarEmoji(), response.title(), response.industry(),
                response.location(), response.remote(), response.skills(), response.experienceYears(), response.rateFloor(),
                response.openTo(), response.careerHealth(), response.consent(), response.autonomy(), response.bio(),
                fullAccess ? response.cvUrl() : null, fullAccess ? response.cvFileName() : null,
                response.locationConsent(), null, null, null,
                response.cameForJob(),
                fullAccess ? response.organization() : null,
                fullAccess ? response.currentCtc() : null,
                fullAccess ? response.expectedCtc() : null,
                fullAccess ? response.preferredLocation() : null,
                fullAccess);
    }

    private EnterpriseProfile requireEnterprise(UUID userId) {
        return enterpriseProfileService.getEntityForUser(userId);
    }
}
