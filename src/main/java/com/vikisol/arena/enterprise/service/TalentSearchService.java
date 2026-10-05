package com.vikisol.arena.enterprise.service;

import com.vikisol.arena.applications.repository.ApplicationRepository;
import com.vikisol.arena.audit.AuditActions;
import com.vikisol.arena.audit.AuditService;
import com.vikisol.arena.career.service.CompensationPolicy;
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

    private final com.vikisol.arena.profile.industry.IndustryCatalogue industryCatalogue;
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
    private final jakarta.persistence.EntityManager entityManager;

    @Transactional(readOnly = true)
    public PagedResponse<TalentSearchResult> search(UUID enterpriseUserId, String text, String industry, boolean remoteOnly, Pageable pageable) {
        EnterpriseProfile enterprise = requireEnterprise(enterpriseUserId);
        Industry industryEnum = (industry == null || industry.isBlank() || "All".equalsIgnoreCase(industry))
                ? null : industryCatalogue.resolve(industry);
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
        Set<UUID> ctcShared = candidateIds.isEmpty() ? Set.of()
                : new HashSet<>(applicationRepository.findCandidateIdsSharingCtcWithEnterprise(candidateIds, enterprise.getId()));

        return PagedResponse.of(page, c -> toResult(c, unlockedIds, appliedIds, ctcShared));
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
        boolean unlocked = unlockedCandidateRepository.existsByEnterpriseIdAndCandidateId(enterprise.getId(), candidate.getId());
        boolean applied = applicationRepository.existsByCandidateIdAndJobPostingEnterpriseId(candidate.getId(), enterprise.getId());
        boolean pay = CompensationPolicy.employerMaySee(applicationRepository.ctcSharedWithEnterprise(candidate.getId(), enterprise.getId()));
        return redactIfLocked(candidateProfileMapper.toResponse(candidate), unlocked || applied, pay);
    }

    @Transactional
    public void unlock(UUID enterpriseUserId, UUID candidateId) {
        EnterpriseProfile enterpriseRef = requireEnterprise(enterpriseUserId);
        // Re-fetched under a row lock (held for the rest of this transaction) before either
        // check below - closes both the same-candidate double-click race and the cross-candidate
        // credit-balance race, since a second concurrent unlock() for this tenant now blocks here
        // until the first transaction commits, then sees its up-to-date state.
        //
        // ARCHITECT-REVIEW-BE-1 SHOULD-FIX, found by this fix's own race test: requireEnterprise()
        // above already loaded this same row (unlocked) into this transaction's Hibernate session.
        // findByIdForUpdate's query DOES still take the DB-level lock and correctly block a
        // concurrent caller - but once unblocked, Hibernate's identity map hands back the SAME
        // already-managed Java instance without overwriting its fields from the fresh query
        // result, so the unblocked caller kept reading the stale unlockCreditsUsed it loaded
        // before ever waiting on the lock. Both callers then independently computed "0 + 1" and
        // the second write clobbered the first - two unlocks for the price of one credit, with
        // the lock appearing to work (it serialized the two transactions) while silently not
        // doing its job (neither read the other's state). An explicit refresh under the lock
        // forces this transaction to see the committed value.
        EnterpriseProfile enterprise = enterpriseProfileRepository.findByIdForUpdate(enterpriseRef.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Enterprise profile not found"));
        entityManager.refresh(enterprise);
        if (unlockedCandidateRepository.existsByEnterpriseIdAndCandidateId(enterprise.getId(), candidateId)) {
            return; // already unlocked - idempotent
        }
        if (enterprise.getUnlockCreditsUsed() >= enterprise.getUnlockCreditsTotal()) {
            throw new BadRequestException("You're out of unlock credits on the " + enterprise.getPlan().wireValue()
                    + " plan. Write to Arena's team for more unlocks.");
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
    private TalentSearchResult toResult(CandidateProfile candidate, Set<UUID> unlockedIds, Set<UUID> appliedIds,
                                        Set<UUID> ctcShared) {
        boolean unlocked = unlockedIds.contains(candidate.getId());
        boolean applied = appliedIds.contains(candidate.getId());
        int matchPercentage = scoringService.computeMatchPercentage(candidate, Set.of(), null);
        boolean pay = CompensationPolicy.employerMaySee(ctcShared.contains(candidate.getId()));
        return new TalentSearchResult(
                redactIfLocked(candidateProfileMapper.toResponse(candidate), unlocked || applied, pay), matchPercentage,
                IndianData.pick(FIT_BLURBS), String.join(", ", candidate.getOpenTo().stream().map(o -> o.wireValue()).toList()),
                unlocked);
    }

    // The only actually-paywalled field is the CV file link; everything else (skills, title,
    // location, career health) is visible pre-unlock so a recruiter can decide whether a candidate
    // is worth a credit. Pay follows CompensationPolicy (private unless the candidate chose
    // otherwise, G21) and the approximate home location is never shown to an enterprise - see
    // CandidateProfileMapper.forEmployer.
    private CandidateProfileResponse redactIfLocked(CandidateProfileResponse response, boolean fullAccess, boolean compensationVisible) {
        return candidateProfileMapper.forEmployer(response, fullAccess, compensationVisible);
    }

    private EnterpriseProfile requireEnterprise(UUID userId) {
        return enterpriseProfileService.getEntityForUser(userId);
    }
}
