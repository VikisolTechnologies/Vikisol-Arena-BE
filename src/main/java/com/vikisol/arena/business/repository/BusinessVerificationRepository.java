package com.vikisol.arena.business.repository;

import com.vikisol.arena.business.entity.BusinessVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BusinessVerificationRepository extends JpaRepository<BusinessVerification, UUID> {
    Optional<BusinessVerification> findByTenantId(UUID tenantId);
}
