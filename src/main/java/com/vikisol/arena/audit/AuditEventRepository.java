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

    @Query("""
            select e from AuditEvent e where e.tenant.id = :tenantId
            and (:actorId is null or e.actor.id = :actorId)
            and (:action is null or e.action = :action)
            and (:since is null or e.createdAt >= :since)
            order by e.createdAt desc
            """)
    Page<AuditEvent> search(@Param("tenantId") UUID tenantId, @Param("actorId") UUID actorId,
                             @Param("action") String action, @Param("since") Instant since, Pageable pageable);

    List<AuditEvent> findByTenantIdAndActorIdAndCreatedAtAfter(UUID tenantId, UUID actorId, Instant since);

    Page<AuditEvent> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
}
