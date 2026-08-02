package com.vikisol.arena.enterprise.repository;

import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EnterpriseProfileRepository extends JpaRepository<EnterpriseProfile, UUID> {
    Optional<EnterpriseProfile> findByUserId(UUID userId);
}
