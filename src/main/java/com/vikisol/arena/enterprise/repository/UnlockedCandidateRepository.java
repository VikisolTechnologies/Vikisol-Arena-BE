package com.vikisol.arena.enterprise.repository;

import com.vikisol.arena.enterprise.entity.UnlockedCandidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UnlockedCandidateRepository extends JpaRepository<UnlockedCandidate, UUID> {
    List<UnlockedCandidate> findByEnterpriseId(UUID enterpriseId);
    boolean existsByEnterpriseIdAndCandidateId(UUID enterpriseId, UUID candidateId);
}
