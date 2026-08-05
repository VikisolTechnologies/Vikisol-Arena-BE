package com.vikisol.arena.profile.service;

import com.vikisol.arena.applications.repository.ApplicationRepository;
import com.vikisol.arena.audit.AuditActions;
import com.vikisol.arena.audit.AuditService;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.common.service.FileStorageService;
import com.vikisol.arena.matching.ScoringService;
import com.vikisol.arena.profile.dto.CandidateDataExport;
import com.vikisol.arena.profile.dto.CandidateProfileResponse;
import com.vikisol.arena.profile.dto.ConsentDto;
import com.vikisol.arena.profile.entity.AutonomyLevel;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.entity.CandidateSkill;
import com.vikisol.arena.profile.entity.ConsentSettings;
import com.vikisol.arena.profile.entity.Industry;
import com.vikisol.arena.profile.entity.OpenTo;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import com.vikisol.arena.security.jwt.JwtTokenProvider;
import com.vikisol.arena.security.jwt.RefreshTokenService;
import com.vikisol.arena.security.jwt.TokenDenylistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CandidateProfileService {

    private final CandidateProfileRepository candidateProfileRepository;
    private final CandidateProfileMapper mapper;
    private final ScoringService scoringService;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;
    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;
    private final RefreshTokenService refreshTokenService;
    private final TokenDenylistService tokenDenylistService;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional(readOnly = true)
    public CandidateProfile getEntityForUser(UUID userId) {
        return candidateProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No candidate profile for this account"));
    }

    @Transactional(readOnly = true)
    public CandidateProfile getEntityById(UUID id) {
        return candidateProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found: " + id));
    }

    @Transactional(readOnly = true)
    public CandidateProfileResponse getMyProfile(UUID userId) {
        return mapper.toResponse(getEntityForUser(userId));
    }

    @Transactional
    public CandidateProfileResponse updateDetails(UUID userId, String name, String title, String industry,
                                                    int experienceYears, int rateFloor, List<String> openTo) {
        CandidateProfile profile = getEntityForUser(userId);
        profile.setName(name);
        profile.setTitle(title);
        profile.setIndustry(Industry.fromWireValue(industry));
        profile.setExperienceYears(experienceYears);
        profile.setRateFloor(rateFloor);
        // Hibernate's @ElementCollection needs a mutable backing list to manage - Stream.toList()
        // returns an immutable one, which blows up with UnsupportedOperationException on flush.
        profile.setOpenTo(new java.util.ArrayList<>(openTo.stream().map(OpenTo::fromWireValue).toList()));
        return saveAndScore(profile);
    }

    @Transactional
    public CandidateProfileResponse updateSkills(UUID userId, List<String> skillNames) {
        CandidateProfile profile = getEntityForUser(userId);
        profile.setSkills(new java.util.ArrayList<>(skillNames.stream().map(n -> new CandidateSkill(n, false)).toList()));
        return saveAndScore(profile);
    }

    // DPDP consent audit log (ARENA-SHIP-IT.md #5) - every change gets a timestamped AuditEvent
    // recording the before/after state, not just the silent overwrite this used to be. Consent
    // withdrawal ("searchableByEnterprises: true -> false") already takes effect immediately
    // wherever it's checked (see TalentSearchService.getConsentView()'s own comment) - this adds
    // the paper trail on top, it doesn't change the enforcement itself.
    @Transactional
    public CandidateProfileResponse updateConsent(UUID userId, ConsentDto consent) {
        CandidateProfile profile = getEntityForUser(userId);
        ConsentSettings before = profile.getConsent();
        profile.setConsent(new ConsentSettings(consent.autoApply(), consent.searchableByEnterprises()));
        CandidateProfileResponse response = saveAndScore(profile);
        auditService.record(null, userId, AuditActions.CONSENT_CHANGED, profile.getName(),
                "autoApply: %s -> %s; searchableByEnterprises: %s -> %s".formatted(
                        before.isAutoApply(), consent.autoApply(), before.isSearchableByEnterprises(), consent.searchableByEnterprises()));
        return response;
    }

    // DPDP data export (ARENA-SHIP-IT.md #5) - the candidate's own profile fields plus their
    // application history, the personal data actually meaningful to a candidate requesting a
    // copy. Deliberately doesn't chase every FK reference across every module (messages,
    // interview notes, marketplace bids) - those are conversations/negotiations involving a
    // second party, not solely-owned personal data, and are a larger scope than this pass covers.
    @Transactional(readOnly = true)
    public CandidateDataExport exportMyData(UUID userId) {
        CandidateProfile profile = getEntityForUser(userId);
        User user = userRepository.findById(userId).orElseThrow();
        var applications = applicationRepository.findByCandidateId(profile.getId(),
                org.springframework.data.domain.Pageable.unpaged()).getContent().stream()
                .map(a -> new CandidateDataExport.ApplicationSummary(
                        a.getJobPosting() != null ? a.getJobPosting().getTitle() : null,
                        a.getStage().wireValue(), a.getAppliedAt().toString()))
                .toList();
        CandidateDataExport export = new CandidateDataExport(
                user.getEmail(), mapper.toResponse(profile), applications, Instant.now().toString());
        auditService.record(null, userId, AuditActions.DATA_EXPORTED, profile.getName());
        return export;
    }

    // DPDP right-to-erasure (ARENA-SHIP-IT.md #5) - anonymizes the profile and disables the
    // account rather than a hard delete: applications/interviews/messages/audit events all hold
    // FK references to this candidate, and cascading a real delete through every one of those
    // safely is a much larger, riskier change than this pass has budget for (see DECISIONS.md).
    // The practical DPDP-meaningful effect is the same either way - the candidate's identifying
    // info (name, bio, skills, CV) is gone and the account can never sign in again.
    @Transactional
    public void deleteMyAccount(UUID userId, String accessToken) {
        CandidateProfile profile = getEntityForUser(userId);
        User user = userRepository.findById(userId).orElseThrow();

        if (profile.getCvUrl() != null) {
            fileStorageService.delete(profile.getCvUrl());
        }
        profile.setName("Deleted user");
        profile.setBio(null);
        profile.setSkills(new java.util.ArrayList<>());
        profile.setCvUrl(null);
        profile.setCvFileName(null);
        profile.setConsent(new ConsentSettings(false, false));
        candidateProfileRepository.save(profile);

        user.setDeletedAt(Instant.now());
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(userId);
        // Without this, the access token making THIS request stays valid for up to its
        // remaining 15min lifetime after "deletion" - long enough to call other endpoints and
        // partially un-anonymize what was just erased. Mirrors AuthService.signOut()'s denylist
        // call exactly.
        if (accessToken != null && jwtTokenProvider.validateToken(accessToken)) {
            tokenDenylistService.denylist(jwtTokenProvider.getJtiFromToken(accessToken), jwtTokenProvider.getExpiryFromToken(accessToken));
        }

        auditService.record(null, userId, AuditActions.ACCOUNT_DELETED, "self-service erasure request");
    }

    @Transactional
    public CandidateProfileResponse updateAutonomy(UUID userId, AutonomyLevel autonomy) {
        CandidateProfile profile = getEntityForUser(userId);
        profile.setAutonomy(autonomy);
        return saveAndScore(profile);
    }

    @Transactional
    public CandidateProfileResponse uploadCv(UUID userId, org.springframework.web.multipart.MultipartFile file) {
        CandidateProfile profile = getEntityForUser(userId);
        FileStorageService.StoredFile stored = fileStorageService.store(file, "candidate-cv", profile.getId().toString(), "cv");
        profile.setCvUrl(stored.url());
        profile.setCvFileName(stored.fileName());
        return saveAndScore(profile);
    }

    private CandidateProfileResponse saveAndScore(CandidateProfile profile) {
        profile.setCareerHealth(scoringService.computeCareerHealth(profile));
        return mapper.toResponse(candidateProfileRepository.save(profile));
    }
}
