package com.vikisol.arena.enterprise.service;

import com.vikisol.arena.audit.AuditActions;
import com.vikisol.arena.audit.AuditService;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.enterprise.dto.admin.BillingResponse;
import com.vikisol.arena.enterprise.dto.admin.ChangePlanRequest;
import com.vikisol.arena.enterprise.dto.admin.InvoiceResponse;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.entity.MembershipStatus;
import com.vikisol.arena.enterprise.entity.Plan;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import com.vikisol.arena.enterprise.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * CA4 (Billing & plan). Real plan-change support, built from scratch here - the existing
 * self-service EnterpriseProfileService.updateMyProfile() never touched plan/seats/credits even
 * before this suite (see the audit-wiring commit's note), so this is the first place a plan
 * change actually flips real gates (posting limits in JobPostingService, unlock credits in
 * TalentSearchService both already read Plan/unlockCreditsTotal directly off EnterpriseProfile).
 * Invoices are mocked (no payment provider) - generated deterministically, not persisted.
 */
@Service
@RequiredArgsConstructor
public class BillingService {

    // Mirrors arena-web's plan.ts SEAT_DEFAULTS/CREDIT_DEFAULTS-equivalent bump on upgrade - a
    // plan change grants the new plan's baseline seats/credits if the tenant's current total is
    // below it, but never *reduces* an admin's already-provisioned total on downgrade (avoids
    // stranding active members over a seat limit mid-downgrade).
    private static final int PRO_SEATS = 10;
    private static final int PRO_CREDITS = 50;
    private static final int ENTERPRISE_SEATS = 50;
    private static final int ENTERPRISE_CREDITS = 200;

    private final EnterpriseProfileService enterpriseProfileService;
    private final EnterpriseProfileRepository enterpriseProfileRepository;
    private final MembershipRepository membershipRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public BillingResponse getBilling(UUID userId) {
        EnterpriseProfile tenant = enterpriseProfileService.getEntityForUser(userId);
        return toResponse(tenant);
    }

    @Transactional
    public BillingResponse changePlan(UUID userId, ChangePlanRequest request) {
        EnterpriseProfile tenant = enterpriseProfileService.getEntityForUser(userId);
        Plan newPlan = Plan.fromWireValue(request.plan());
        if (newPlan == tenant.getPlan()) return toResponse(tenant);

        Plan oldPlan = tenant.getPlan();
        tenant.setPlan(newPlan);
        if (newPlan == Plan.PRO) {
            tenant.setSeatsTotal(Math.max(tenant.getSeatsTotal(), PRO_SEATS));
            tenant.setUnlockCreditsTotal(Math.max(tenant.getUnlockCreditsTotal(), PRO_CREDITS));
        } else if (newPlan == Plan.ENTERPRISE) {
            tenant.setSeatsTotal(Math.max(tenant.getSeatsTotal(), ENTERPRISE_SEATS));
            tenant.setUnlockCreditsTotal(Math.max(tenant.getUnlockCreditsTotal(), ENTERPRISE_CREDITS));
        }
        enterpriseProfileRepository.save(tenant);

        auditService.record(tenant.getId(), userId, AuditActions.PLAN_CHANGED,
                oldPlan.wireValue() + " -> " + newPlan.wireValue());
        return toResponse(tenant);
    }

    private BillingResponse toResponse(EnterpriseProfile tenant) {
        // seatsUsed comes from live Membership rows, not the stale EnterpriseProfile.seatsUsed
        // field - that field predates Membership existing at all (back when every tenant had
        // exactly one user) and nothing keeps it in sync with team invites/removals anymore.
        // TeamPage already computes this correctly the same way; billing needs to match it.
        long seatsUsed = membershipRepository.countByTenantIdAndStatus(tenant.getId(), MembershipStatus.ACTIVE);
        return new BillingResponse(
                tenant.getPlan().wireValue(), (int) seatsUsed, tenant.getSeatsTotal(),
                tenant.getUnlockCreditsUsed(), tenant.getUnlockCreditsTotal(), mockInvoices(tenant));
    }

    private List<InvoiceResponse> mockInvoices(EnterpriseProfile tenant) {
        if (tenant.getPlan() == Plan.FREE) return List.of();
        String amount = tenant.getPlan() == Plan.ENTERPRISE ? "Custom" : "₹4,999";
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneOffset.UTC);
        var created = tenant.getCreatedAt();
        return List.of(
                new InvoiceResponse("INV-" + tenant.getId().toString().substring(0, 8) + "-1",
                        fmt.format(created), amount, "paid"),
                new InvoiceResponse("INV-" + tenant.getId().toString().substring(0, 8) + "-2",
                        fmt.format(created.plusSeconds(30L * 24 * 3600)), amount, "paid"));
    }
}
