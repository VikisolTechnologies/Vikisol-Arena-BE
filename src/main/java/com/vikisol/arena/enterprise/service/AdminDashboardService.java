package com.vikisol.arena.enterprise.service;

import com.vikisol.arena.audit.AuditActions;
import com.vikisol.arena.audit.AuditEventRepository;
import com.vikisol.arena.audit.AuditEventRepository.AuditActionCount;
import com.vikisol.arena.audit.AuditEventRepository.AuditActionTimestamp;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
        List<UUID> actorIds = members.stream().map(m -> m.getUser().getId()).toList();

        // Two SQL aggregate queries for the whole team at once (COUNT/GROUP BY, and a targeted
        // timestamp-only fetch for the one action that needs actual times, not just a count)
        // instead of one full unindexed-in-Java query per team member - see AuditEventRepository.
        Map<UUID, Map<String, Long>> countsByActor = batchActionCounts(tenant.getId(), actorIds, since);
        Map<UUID, List<Instant>> stageMoveTimesByActor = batchStageMoveTimestamps(tenant.getId(), actorIds, since);

        List<AdminDashboardResponse.RecruiterActivity> activity = members.stream()
                .map(m -> activityFor(m, countsByActor.getOrDefault(m.getUser().getId(), Map.of()),
                        stageMoveTimesByActor.getOrDefault(m.getUser().getId(), List.of())))
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

    // One query for every member's per-action counts across the whole team (instead of one query
    // per member, each pulling and counting full audit rows in Java).
    private Map<UUID, Map<String, Long>> batchActionCounts(UUID tenantId, List<UUID> actorIds, Instant since) {
        if (actorIds.isEmpty()) return Map.of();
        return auditEventRepository.countActionsByActorIn(tenantId, actorIds, since).stream()
                .collect(Collectors.groupingBy(AuditActionCount::getActorId,
                        Collectors.toMap(AuditActionCount::getAction, AuditActionCount::getCount)));
    }

    // One query for every member's STAGE_MOVED timestamps across the whole team (instead of one
    // query per member) - avgHoursBetweenStageMoves needs the actual times, not just a count.
    private Map<UUID, List<Instant>> batchStageMoveTimestamps(UUID tenantId, List<UUID> actorIds, Instant since) {
        if (actorIds.isEmpty()) return Map.of();
        return auditEventRepository.findActionTimestampsByActorIn(tenantId, actorIds, AuditActions.STAGE_MOVED, since).stream()
                .collect(Collectors.groupingBy(AuditActionTimestamp::getActorId,
                        Collectors.mapping(AuditActionTimestamp::getCreatedAt, Collectors.toList())));
    }

    private AdminDashboardResponse.RecruiterActivity activityFor(Membership member, Map<String, Long> counts, List<Instant> stageMoveTimesUnsorted) {
        UUID actorId = member.getUser().getId();

        int postings = counts.getOrDefault(AuditActions.POSTING_CREATED, 0L).intValue();
        int unlocks = counts.getOrDefault(AuditActions.CANDIDATE_UNLOCKED, 0L).intValue();
        int interviews = counts.getOrDefault(AuditActions.INTERVIEW_SCHEDULED, 0L).intValue();
        int messages = counts.getOrDefault(AuditActions.MESSAGE_SENT, 0L).intValue();

        // Query already orders by createdAt, but re-sort defensively - this list is tiny (one
        // member's stage moves in range), so it costs nothing to not depend on stream-collector
        // ordering guarantees.
        List<Instant> stageMoveTimes = stageMoveTimesUnsorted.stream().sorted().toList();

        Double avgGapHours = null;
        if (stageMoveTimes.size() > 1) {
            long totalSeconds = ChronoUnit.SECONDS.between(stageMoveTimes.get(0), stageMoveTimes.get(stageMoveTimes.size() - 1));
            avgGapHours = (totalSeconds / 3600.0) / (stageMoveTimes.size() - 1);
        }

        return new AdminDashboardResponse.RecruiterActivity(
                actorId.toString(), member.getUser().getName(), member.getUser().getRole().wireValue(),
                postings, unlocks, stageMoveTimes.size(), interviews, messages, avgGapHours);
    }
}
