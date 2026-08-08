package com.vikisol.arena.applications.repository;

import com.vikisol.arena.applications.entity.Application;
import com.vikisol.arena.jobs.entity.JobPosting;
import com.vikisol.arena.profile.entity.CandidateProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {
    Page<Application> findByCandidateId(UUID candidateId, Pageable pageable);
    Page<Application> findByJobPostingId(UUID jobPostingId, Pageable pageable);
    Optional<Application> findByCandidateIdAndJobPostingId(UUID candidateId, UUID jobPostingId);
    boolean existsByCandidateIdAndJobPostingId(UUID candidateId, UUID jobPostingId);
    boolean existsByCandidateIdAndJobPostingEnterpriseId(UUID candidateId, UUID enterpriseId);
    java.util.List<Application> findByCandidate(CandidateProfile candidate);
    java.util.List<Application> findByJobPosting(JobPosting jobPosting);

    // Batched form of existsByCandidateIdAndJobPostingEnterpriseId for a whole page of candidates
    // at once - one query instead of one-per-row. Callers turn this into a Set for O(1) lookups.
    @Query("select distinct a.candidate.id from Application a where a.candidate.id in :candidateIds and a.jobPosting.enterprise.id = :enterpriseId")
    List<UUID> findCandidateIdsWithApplicationToEnterprise(@Param("candidateIds") List<UUID> candidateIds, @Param("enterpriseId") UUID enterpriseId);
}
