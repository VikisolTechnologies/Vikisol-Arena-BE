package com.vikisol.arena.enterprise.service;

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

    @Transactional(readOnly = true)
    public PagedResponse<TalentSearchResult> search(UUID enterpriseUserId, String text, String industry, boolean remoteOnly, Pageable pageable) {
        EnterpriseProfile enterprise = requireEnterprise(enterpriseUserId);
        Industry industryEnum = (industry == null || industry.isBlank() || "All".equalsIgnoreCase(industry))
                ? null : Industry.fromWireValue(industry);
        // Always a non-null string ("" means "no filter") - the repository query relies on this,
        // see the comment on CandidateProfileRepository.search().
        String normalizedText = (text == null || text.isBlank()) ? "" : text.toLowerCase();

        return PagedResponse.of(
                candidateProfileRepository.search(normalizedText, industryEnum, remoteOnly, pageable),
                c -> toResult(c, enterprise));
    }

    @Transactional(readOnly = true)
    public CandidateProfileResponse getCandidateDetail(UUID id) {
        CandidateProfile candidate = candidateProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found: " + id));
        return candidateProfileMapper.toResponse(candidate);
    }

    @Transactional
    public void unlock(UUID enterpriseUserId, UUID candidateId) {
        EnterpriseProfile enterprise = requireEnterprise(enterpriseUserId);
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

    private TalentSearchResult toResult(CandidateProfile candidate, EnterpriseProfile enterprise) {
        boolean unlocked = unlockedCandidateRepository.existsByEnterpriseIdAndCandidateId(enterprise.getId(), candidate.getId());
        int matchPercentage = scoringService.computeMatchPercentage(candidate, Set.of(), null);
        return new TalentSearchResult(
                candidateProfileMapper.toResponse(candidate), matchPercentage,
                IndianData.pick(FIT_BLURBS), String.join(", ", candidate.getOpenTo().stream().map(o -> o.wireValue()).toList()),
                unlocked);
    }

    private EnterpriseProfile requireEnterprise(UUID userId) {
        return enterpriseProfileService.getEntityForUser(userId);
    }
}
