package com.vikisol.arena.profile.service;

import com.vikisol.arena.profile.dto.CandidateProfileResponse;
import com.vikisol.arena.profile.dto.ConsentDto;
import com.vikisol.arena.profile.dto.SkillDto;
import com.vikisol.arena.profile.entity.CandidateProfile;
import org.springframework.stereotype.Component;

@Component
public class CandidateProfileMapper {

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
                p.getCvUrl(),
                p.getCvFileName()
        );
    }
}
