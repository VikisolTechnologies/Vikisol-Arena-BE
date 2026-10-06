package com.vikisol.arena.profile.service;

import com.vikisol.arena.common.service.FileSigningService;
import com.vikisol.arena.profile.dto.CandidateProfileResponse;
import com.vikisol.arena.profile.dto.ConsentDto;
import com.vikisol.arena.profile.dto.SkillDto;
import com.vikisol.arena.profile.entity.CandidateProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CandidateProfileMapper {

    private final FileSigningService fileSigningService;

    // What an employer (recruiter, company admin, hiring manager) may see of a candidate:
    // - the CV and the job-seeker fields only with full access (they applied, or were unlocked);
    // - pay (current/expected CTC) only when CompensationPolicy allows it - private by default;
    // - never the approximate home location (location consent is for peer discovery only).
    public CandidateProfileResponse forEmployer(CandidateProfileResponse r, boolean fullAccess, boolean compensationVisible) {
        return new CandidateProfileResponse(
                r.id(), r.name(), r.avatarEmoji(), r.title(), r.industry(),
                r.location(), r.remote(), r.skills(), r.experienceYears(), r.rateFloor(),
                r.openTo(), r.careerHealth(), r.consent(), r.autonomy(), r.bio(),
                fullAccess ? r.cvUrl() : null, fullAccess ? r.cvFileName() : null,
                r.locationConsent(), null, null, null,
                r.cameForJob(),
                fullAccess ? r.organization() : null,
                fullAccess && compensationVisible ? r.currentCtc() : null,
                fullAccess && compensationVisible ? r.expectedCtc() : null,
                fullAccess ? r.preferredLocation() : null,
                fullAccess);
    }

    public CandidateProfileResponse toResponse(CandidateProfile p) {
        return new CandidateProfileResponse(
                p.getId().toString(),
                p.getName(),
                p.getAvatarEmoji(),
                p.getTitle(),
                p.getIndustry().wireValue(),
                p.getLocation(),
                p.isRemote(),
                p.getSkills().stream().map(s -> new SkillDto(s.getName(), s.isVerified())).toList(),
                p.getExperienceYears(),
                p.getRateFloor(),
                p.getOpenTo().stream().map(o -> o.wireValue()).toList(),
                p.getCareerHealth(),
                new ConsentDto(p.getConsent().isAutoApply(), p.getConsent().isSearchableByEnterprises()),
                p.getAutonomy().wireValue(),
                p.getBio(),
                fileSigningService.sign(p.getCvUrl()),
                p.getCvFileName(),
                p.getLocationConsent().wireValue(),
                p.getHomeCity(),
                p.getApproxLat(),
                p.getApproxLng(),
                p.getCameForJob(),
                p.getOrganization(),
                p.getCurrentCtc(),
                p.getExpectedCtc(),
                p.getPreferredLocation(),
                true
        );
    }
}
