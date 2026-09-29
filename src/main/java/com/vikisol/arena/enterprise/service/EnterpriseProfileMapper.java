package com.vikisol.arena.enterprise.service;

import com.vikisol.arena.business.repository.BusinessVerificationRepository;
import com.vikisol.arena.enterprise.dto.EnterpriseProfileResponse;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EnterpriseProfileMapper {

    private final BusinessVerificationRepository verificationRepository;

    public EnterpriseProfileResponse toResponse(EnterpriseProfile p) {
        var verification = p.getId() == null ? null : verificationRepository.findByTenantId(p.getId()).orElse(null);
        return new EnterpriseProfileResponse(
                p.getCompanyName(), p.getLogoEmoji(), p.getIndustry().wireValue(), p.getSize().wireValue(),
                p.getHiringFor(), p.getPlan().wireValue(), p.getSeatsUsed(), p.getSeatsTotal(),
                p.getUnlockCreditsUsed(), p.getUnlockCreditsTotal(), p.getStatus().wireValue(),
                p.getWebsite(), p.getGstin(), p.getCin(), p.getHqCity(), p.getLogoUrl(),
                verification == null ? "none" : verification.getStatus().name().toLowerCase(),
                verification == null ? null : verification.getReviewNote());
    }
}
