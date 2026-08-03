package com.vikisol.arena.audit;

import com.vikisol.arena.audit.entity.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    Page<AuditEvent> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
}
