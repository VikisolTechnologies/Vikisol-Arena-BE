package com.vikisol.arena.platform.service;

import com.vikisol.arena.audit.AuditActions;
import com.vikisol.arena.audit.AuditService;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.enterprise.entity.CreditLedgerEntry;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.entity.MembershipStatus;
import com.vikisol.arena.enterprise.entity.Plan;
import com.vikisol.arena.enterprise.entity.TenantStatus;
import com.vikisol.arena.enterprise.repository.CreditLedgerRepository;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import com.vikisol.arena.enterprise.repository.MembershipRepository;
import com.vikisol.arena.platform.dto.AdjustSubscriptionRequest;
import com.vikisol.arena.platform.dto.TenantSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * PA1 (tenants list/suspend/reactivate) + PA2 (subscriptions - manual plan/seat/credit changes,
 * audited). This is the single most consequential service in the platform_admin console (see
 * DECISIONS.md's "most dangerous surface" framing) - every mutation here is audited, and credit
 * grants are also written to CreditLedgerEntry so they show up in the tenant's own billing
 * history, not just the global audit log.
 */
@Service
@RequiredArgsConstructor
public class PlatformTenantService {

    private final EnterpriseProfileRepository enterpriseProfileRepository;
    private final MembershipRepository membershipRepository;
    private final CreditLedgerRepository creditLedgerRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PagedResponse<TenantSummaryResponse> listTenants(String query, Pageable pageable) {
        String q = query == null ? "" : query.trim();
        return PagedResponse.of(enterpriseProfileRepository.search(q, pageable), this::toSummary);
    }

    @Transactional
    public void setSuspended(UUID actorUserId, UUID tenantId, boolean suspended) {
        EnterpriseProfile tenant = requireTenant(tenantId);
        tenant.setStatus(suspended ? TenantStatus.SUSPENDED : TenantStatus.ACTIVE);
        enterpriseProfileRepository.save(tenant);
        auditService.record(tenant.getId(), actorUserId,
                suspended ? AuditActions.TENANT_SUSPENDED : AuditActions.TENANT_REACTIVATED, tenant.getCompanyName());
    }

    @Transactional
    public TenantSummaryResponse adjustSubscription(UUID actorUserId, UUID tenantId, AdjustSubscriptionRequest request) {
        EnterpriseProfile tenant = requireTenant(tenantId);
        StringBuilder changes = new StringBuilder();

        if (request.plan() != null && !request.plan().isBlank()) {
            Plan newPlan = Plan.fromWireValue(request.plan());
            if (newPlan != tenant.getPlan()) {
                changes.append("plan ").append(tenant.getPlan().wireValue()).append("->").append(newPlan.wireValue()).append("; ");
                tenant.setPlan(newPlan);
            }
        }
        if (request.seatsTotal() != null && !request.seatsTotal().equals(tenant.getSeatsTotal())) {
            if (request.seatsTotal() < 1) throw new BadRequestException("Seat total must be at least 1.");
            changes.append("seats ").append(tenant.getSeatsTotal()).append("->").append(request.seatsTotal()).append("; ");
            tenant.setSeatsTotal(request.seatsTotal());
        }
        if (request.creditDelta() != null && request.creditDelta() != 0) {
            int newTotal = tenant.getUnlockCreditsTotal() + request.creditDelta();
            if (newTotal < tenant.getUnlockCreditsUsed()) {
                throw new BadRequestException("Credit total can't drop below credits already used.");
            }
            tenant.setUnlockCreditsTotal(newTotal);
            creditLedgerRepository.save(CreditLedgerEntry.builder()
                    .tenant(tenant).actor(userRepository.getReferenceById(actorUserId))
                    .delta(request.creditDelta()).reason("platform_admin: " + request.reason())
                    .balanceAfter(newTotal - tenant.getUnlockCreditsUsed())
                    .build());
            changes.append("credits ").append(request.creditDelta() > 0 ? "+" : "").append(request.creditDelta()).append("; ");
        }

        enterpriseProfileRepository.save(tenant);
        if (!changes.isEmpty()) {
            auditService.record(tenant.getId(), actorUserId, AuditActions.SUBSCRIPTION_ADJUSTED,
                    tenant.getCompanyName(), changes + "reason: " + request.reason());
        }
        return toSummary(tenant);
    }

    private EnterpriseProfile requireTenant(UUID id) {
        return enterpriseProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + id));
    }

    private TenantSummaryResponse toSummary(EnterpriseProfile t) {
        long seatsUsed = membershipRepository.countByTenantIdAndStatus(t.getId(), MembershipStatus.ACTIVE);
        return new TenantSummaryResponse(
                t.getId().toString(), t.getCompanyName(), t.getLogoEmoji(), t.getPlan().wireValue(), t.getStatus().wireValue(),
                (int) seatsUsed, t.getSeatsTotal(), t.getUnlockCreditsUsed(), t.getUnlockCreditsTotal(),
                t.getUser() != null ? t.getUser().getEmail() : null, t.getCreatedAt().toString());
    }
}
