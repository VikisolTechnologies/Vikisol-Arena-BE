package com.vikisol.arena.enterprise.service;

import com.vikisol.arena.audit.AuditActions;
import com.vikisol.arena.audit.AuditEventRepository;
import com.vikisol.arena.audit.entity.AuditEvent;
import com.vikisol.arena.enterprise.dto.admin.AdminDashboardResponse;
import com.vikisol.arena.enterprise.entity.CreditLedgerEntry;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.entity.Membership;
import com.vikisol.arena.enterprise.entity.MembershipStatus;
import com.vikisol.arena.enterprise.repository.CreditLedgerRepository;
import com.vikisol.arena.enterprise.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * CA1 (Admin dashboard): per-recruiter activity + team totals + credit balance/burn, derived
 * from the audit log rather than reverse-engineering counts from JobPosting/Application (which
 * don't track *who* on the tenant did what - only tenant-level ownership). The audit trail this
 * suite adds at every meaningful action site already *is* the activity feed CA1 asks for.
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final EnterpriseProfileService enterpriseProfileService;
    private final MembershipRepository membershipRepository;
    private final AuditEventRepository auditEventRepository;
    private final CreditLedgerRepository creditLedgerRepository;

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard(UUID adminUserId, int rangeDays) {
        EnterpriseProfile tenant = enterpriseProfileService.getEntityForUser(adminUserId);
        Instant since = Instant.now().minus(rangeDays, ChronoUnit.DAYS);

        List<Membership> members = membershipRepository.findByTenantIdAndStatus(tenant.getId(), MembershipStatus.ACTIVE);

        List<AdminDashboardResponse.RecruiterActivity> activity = members.stream()
                .map(m -> activityFor(tenant.getId(), m, since))
                .toList();

        int postings = activity.stream().mapToInt(AdminDashboardResponse.RecruiterActivity::postings).sum();
        int unlocks = activity.stream().mapToInt(AdminDashboardResponse.RecruiterActivity::unlocks).sum();
        int stageMoves = activity.stream().mapToInt(AdminDashboardResponse.RecruiterActivity::stageMoves).sum();
        int interviews = activity.stream().mapToInt(AdminDashboardResponse.RecruiterActivity::interviewsHeld).sum();
        int messages = activity.stream().mapToInt(AdminDashboardResponse.RecruiterActivity::messagesSent).sum();

        int creditsSpentInRange = -creditLedgerRepository.findByTenantIdAndCreatedAtAfter(tenant.getId(), since).stream()
                .mapToInt(CreditLedgerEntry::getDelta).filter(d -> d < 0).sum();

        return new AdminDashboardResponse(
                rangeDays,
                new AdminDashboardResponse.TeamTotals(postings, unlocks, stageMoves, interviews, messages),
                activity,
                tenant.getUnlockCreditsTotal() - tenant.getUnlockCreditsUsed(),
                tenant.getUnlockCreditsTotal(),
                creditsSpentInRange);
    }

    private AdminDashboardResponse.RecruiterActivity activityFor(UUID tenantId, Membership member, Instant since) {
        UUID actorId = member.getUser().getId();
        List<AuditEvent> events = auditEventRepository.findByTenantIdAndActorIdAndCreatedAtAfter(tenantId, actorId, since);

        int postings = countAction(events, AuditActions.POSTING_CREATED);
        int unlocks = countAction(events, AuditActions.CANDIDATE_UNLOCKED);
        int interviews = countAction(events, AuditActions.INTERVIEW_SCHEDULED);
        int messages = countAction(events, AuditActions.MESSAGE_SENT);

        List<Instant> stageMoveTimes = events.stream()
                .filter(e -> e.getAction().equals(AuditActions.STAGE_MOVED))
                .map(AuditEvent::getCreatedAt)
                .sorted()
                .toList();

        Double avgGapHours = null;
        if (stageMoveTimes.size() > 1) {
            long totalSeconds = ChronoUnit.SECONDS.between(stageMoveTimes.get(0), stageMoveTimes.get(stageMoveTimes.size() - 1));
            avgGapHours = (totalSeconds / 3600.0) / (stageMoveTimes.size() - 1);
        }

        return new AdminDashboardResponse.RecruiterActivity(
                actorId.toString(), member.getUser().getName(), member.getUser().getRole().wireValue(),
                postings, unlocks, stageMoveTimes.size(), interviews, messages, avgGapHours);
    }

    private int countAction(List<AuditEvent> events, String action) {
        return (int) events.stream().filter(e -> e.getAction().equals(action)).count();
    }
}
