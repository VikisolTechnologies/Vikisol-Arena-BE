package com.vikisol.arena.business.repository;

import com.vikisol.arena.business.entity.BusinessVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BusinessVerificationRepository extends JpaRepository<BusinessVerification, UUID> {
    Optional<BusinessVerification> findByTenantId(UUID tenantId);

    // Row 22 (companyVerified) for a page of jobs in one query.
    @org.springframework.data.jpa.repository.Query("select v.tenant.id from BusinessVerification v where v.tenant.id in :tenantIds and v.status = com.vikisol.arena.business.entity.BusinessVerification.Status.VERIFIED")
    java.util.List<UUID> findVerifiedTenantIds(@org.springframework.data.repository.query.Param("tenantIds") java.util.Collection<UUID> tenantIds);
}
