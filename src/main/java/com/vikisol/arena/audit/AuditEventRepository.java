package com.vikisol.arena.audit;

import com.vikisol.arena.audit.entity.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    // actorId/action=null and since=Instant.EPOCH (never actual null) mean "no filter" - a
    // `:param is null or ... :param ...` pattern is unsafe once :param is also used in a typed
    // comparison (`>=`), because Postgres/JDBC can't infer a consistent bind type when the same
    // parameter is sometimes bound null (see CandidateProfileRepository.search()'s identical
    // fix earlier this session - same root cause, this time for the `since` Instant param).
    // actorId/action stay nullable since UUID/String equality checks don't hit the same
    // ambiguity, but AuditService.search()/exportAll() are the only callers and always pass a
    // real Instant now, so :since itself never actually binds null here in practice either.
    // `actor` is a single-valued (@ManyToOne) association - eagerly fetching it alongside Pageable
    // is safe (unlike a collection fetch, it can't multiply rows or force in-memory pagination -
    // see CandidateProfileRepository.search()'s comment for that distinction). search()/
    // exportAll()/recentGlobal() in AuditService all read e.getActor().getName() per row, up to
    // 5000 rows for a CSV export, so this turns that into zero extra queries instead of one per row.
    @EntityGraph(attributePaths = "actor")
    @Query("""
            select e from AuditEvent e where e.tenant.id = :tenantId
            and (:actorId is null or e.actor.id = :actorId)
            and (:action is null or e.action = :action)
            and e.createdAt >= :since
            order by e.createdAt desc
            """)
    Page<AuditEvent> search(@Param("tenantId") UUID tenantId, @Param("actorId") UUID actorId,
                             @Param("action") String action, @Param("since") Instant since, Pageable pageable);

    List<AuditEvent> findByTenantIdAndActorIdAndCreatedAtAfter(UUID tenantId, UUID actorId, Instant since);

    // AdminDashboardService.getDashboard() used to call the finder above once per team member and
    // count actions in Java, pulling every column (including the TEXT metadata blob) of every
    // matching row for every member. This does the counting in SQL, for every member's every
    // action type, in one query - see AuditActionCount for the projection shape.
    @Query("""
            select e.actor.id as actorId, e.action as action, count(e) as count
            from AuditEvent e
            where e.tenant.id = :tenantId and e.actor.id in :actorIds and e.createdAt >= :since
            group by e.actor.id, e.action
            """)
    List<AuditActionCount> countActionsByActorIn(@Param("tenantId") UUID tenantId, @Param("actorIds") List<UUID> actorIds,
                                                  @Param("since") Instant since);

    // Companion to the above: avgHoursBetweenStageMoves needs the actual STAGE_MOVED timestamps
    // (not just a count) to measure the gap between a member's first and last move in range - this
    // pulls only actor id + createdAt for that one action, still one query for every member at
    // once instead of one per member.
    @Query("""
            select e.actor.id as actorId, e.createdAt as createdAt
            from AuditEvent e
            where e.tenant.id = :tenantId and e.actor.id in :actorIds and e.action = :action and e.createdAt >= :since
            order by e.actor.id, e.createdAt
            """)
    List<AuditActionTimestamp> findActionTimestampsByActorIn(@Param("tenantId") UUID tenantId, @Param("actorIds") List<UUID> actorIds,
                                                               @Param("action") String action, @Param("since") Instant since);

    Page<AuditEvent> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    // PA1 dashboard's cross-tenant recent-activity feed - unlike search() above, this is
    // deliberately not tenant-scoped (platform_admin has no tenant of its own, see
    // DECISIONS.md), so a plain derived query is fine (no null-parameter ambiguity to work
    // around when there's no parameter at all).
    @EntityGraph(attributePaths = "actor")
    Page<AuditEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // Interface projections backing countActionsByActorIn/findActionTimestampsByActorIn above.
    interface AuditActionCount {
        UUID getActorId();
        String getAction();
        long getCount();
    }

    interface AuditActionTimestamp {
        UUID getActorId();
        Instant getCreatedAt();
    }
}
