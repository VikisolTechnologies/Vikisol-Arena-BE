package com.vikisol.arena.profile.service;

import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.common.service.FileStorageService;
import com.vikisol.arena.matching.ScoringService;
import com.vikisol.arena.profile.dto.CandidateProfileResponse;
import com.vikisol.arena.profile.dto.ConsentDto;
import com.vikisol.arena.profile.entity.AutonomyLevel;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.entity.CandidateSkill;
import com.vikisol.arena.profile.entity.ConsentSettings;
import com.vikisol.arena.profile.entity.Industry;
import com.vikisol.arena.profile.entity.OpenTo;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CandidateProfileService {

    private final CandidateProfileRepository candidateProfileRepository;
    private final CandidateProfileMapper mapper;
    private final ScoringService scoringService;
    private final FileStorageService fileStorageService;

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

    @Transactional
    public CandidateProfileResponse updateConsent(UUID userId, ConsentDto consent) {
        CandidateProfile profile = getEntityForUser(userId);
        profile.setConsent(new ConsentSettings(consent.autoApply(), consent.searchableByEnterprises()));
        return saveAndScore(profile);
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
