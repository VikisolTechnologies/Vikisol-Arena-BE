package com.vikisol.arena.hiring.repository;

import com.vikisol.arena.hiring.entity.ApplicationEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationEvidenceRepository extends JpaRepository<ApplicationEvidence, UUID> {
    List<ApplicationEvidence> findByApplicationId(UUID applicationId);

    List<ApplicationEvidence> findByApplicationIdIn(Collection<UUID> applicationIds);

    Optional<ApplicationEvidence> findByApplicationIdAndRequirementId(UUID applicationId, UUID requirementId);

    boolean existsByRequirementPostingId(UUID postingId);
}
