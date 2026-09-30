package com.vikisol.arena.enterprise.service;

import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.enterprise.dto.admin.BillingResponse;
import com.vikisol.arena.enterprise.dto.admin.ChangePlanRequest;
import com.vikisol.arena.enterprise.dto.admin.InvoiceResponse;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.entity.MembershipStatus;
import com.vikisol.arena.enterprise.entity.Plan;
import com.vikisol.arena.enterprise.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CA4 (Billing & plan). Real plan-change support, built from scratch here - the existing
 * self-service EnterpriseProfileService.updateMyProfile() never touched plan/seats/credits even
 * before this suite (see the audit-wiring commit's note), so this is the first place a plan
 * change actually flips real gates (posting limits in JobPostingService, unlock credits in
 * TalentSearchService both already read Plan/unlockCreditsTotal directly off EnterpriseProfile).
 * Display-only for launch (DECISIONS.md, 30 Sep 2026): no payment provider, no invoices, and
 * no self-service plan change.
 */
@Service
@RequiredArgsConstructor
public class BillingService {

    private final EnterpriseProfileService enterpriseProfileService;
    private final MembershipRepository membershipRepository;

    @Transactional(readOnly = true)
    public BillingResponse getBilling(UUID userId) {
        EnterpriseProfile tenant = enterpriseProfileService.getEntityForUser(userId);
        return toResponse(tenant);
    }

    // Architect decision 30 Sep 2026: billing is display-only for launch - no payments, so no
    // self-service plan change either (it used to grant a paid plan's seats and credits for free).
    // Plans are set by Arena's team until checkout exists.
    @Transactional
    public BillingResponse changePlan(UUID userId, ChangePlanRequest request) {
        EnterpriseProfile tenant = enterpriseProfileService.getEntityForUser(userId);
        Plan newPlan = Plan.fromWireValue(request.plan());
        if (newPlan == tenant.getPlan()) return toResponse(tenant);
        throw new BadRequestException("Plan changes aren't available in the app yet. Write to Arena's team to change your plan.");
    }

    private BillingResponse toResponse(EnterpriseProfile tenant) {
        // seatsUsed comes from live Membership rows, not the stale EnterpriseProfile.seatsUsed
        // field - that field predates Membership existing at all (back when every tenant had
        // exactly one user) and nothing keeps it in sync with team invites/removals anymore.
        // TeamPage already computes this correctly the same way; billing needs to match it.
        long seatsUsed = membershipRepository.countByTenantIdAndStatus(tenant.getId(), MembershipStatus.ACTIVE);
        return new BillingResponse(
                tenant.getPlan().wireValue(), (int) seatsUsed, tenant.getSeatsTotal(),
                tenant.getUnlockCreditsUsed(), tenant.getUnlockCreditsTotal(), invoices(tenant));
    }

    // No payments are taken yet, so there are no invoices to show (these used to be made-up
    // "paid" ones).
    private List<InvoiceResponse> invoices(EnterpriseProfile tenant) {
        return List.of();
    }
}
