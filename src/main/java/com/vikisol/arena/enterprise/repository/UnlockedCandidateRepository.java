package com.vikisol.arena.enterprise.repository;

import com.vikisol.arena.enterprise.entity.UnlockedCandidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UnlockedCandidateRepository extends JpaRepository<UnlockedCandidate, UUID> {
    List<UnlockedCandidate> findByEnterpriseId(UUID enterpriseId);
    boolean existsByEnterpriseIdAndCandidateId(UUID enterpriseId, UUID candidateId);

    // Batched form of existsByEnterpriseIdAndCandidateId for a whole page of candidates at once -
    // one query instead of one-per-row. Callers turn this into a Set for O(1) per-row lookups.
    @Query("select u.candidate.id from UnlockedCandidate u where u.enterprise.id = :enterpriseId and u.candidate.id in :candidateIds")
    List<UUID> findUnlockedCandidateIds(@Param("enterpriseId") UUID enterpriseId, @Param("candidateIds") List<UUID> candidateIds);

    // DemoContentService.removeAll() - see ShortlistEntryRepository.deleteByCandidateId's own comment.
    void deleteByCandidateId(UUID candidateId);
}
