package com.vikisol.arena.enterprise.repository;

import com.vikisol.arena.enterprise.entity.ShortlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShortlistEntryRepository extends JpaRepository<ShortlistEntry, UUID> {
    List<ShortlistEntry> findByEnterpriseId(UUID enterpriseId);
    Optional<ShortlistEntry> findByEnterpriseIdAndCandidateId(UUID enterpriseId, UUID candidateId);

    // DemoContentService.removeAll() - a real enterprise could shortlist a demo candidate during
    // the review window.
    void deleteByCandidateId(UUID candidateId);
}
