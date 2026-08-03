package com.vikisol.arena.enterprise.repository;

import com.vikisol.arena.enterprise.entity.CreditLedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CreditLedgerRepository extends JpaRepository<CreditLedgerEntry, UUID> {
    Page<CreditLedgerEntry> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
    List<CreditLedgerEntry> findByTenantIdAndCreatedAtAfter(UUID tenantId, Instant since);
    List<CreditLedgerEntry> findByTenantIdAndActorIdAndCreatedAtAfter(UUID tenantId, UUID actorId, Instant since);
}
