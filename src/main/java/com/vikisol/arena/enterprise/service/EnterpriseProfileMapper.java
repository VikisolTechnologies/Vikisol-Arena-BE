package com.vikisol.arena.enterprise.service;

import com.vikisol.arena.enterprise.dto.EnterpriseProfileResponse;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import org.springframework.stereotype.Component;

@Component
public class EnterpriseProfileMapper {

    public EnterpriseProfileResponse toResponse(EnterpriseProfile p) {
        return new EnterpriseProfileResponse(
                p.getCompanyName(), p.getLogoEmoji(), p.getIndustry().wireValue(), p.getSize().wireValue(),
                p.getHiringFor(), p.getPlan().wireValue(), p.getSeatsUsed(), p.getSeatsTotal(),
                p.getUnlockCreditsUsed(), p.getUnlockCreditsTotal(), p.getStatus().wireValue());
    }
}
