package com.vikisol.arena.enterprise.repository;

import com.vikisol.arena.enterprise.entity.Membership;
import com.vikisol.arena.enterprise.entity.MembershipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {
    Optional<Membership> findByUserId(UUID userId);
    List<Membership> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    Page<Membership> findByTenantId(UUID tenantId, Pageable pageable);
    List<Membership> findByTenantIdAndStatus(UUID tenantId, MembershipStatus status);
    long countByTenantIdAndStatus(UUID tenantId, MembershipStatus status);
}
