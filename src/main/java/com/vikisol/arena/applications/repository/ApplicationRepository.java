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

    // G25 funnel: applications per stage for one posting.
    @org.springframework.data.jpa.repository.Query(
            "select a.stage as stage, count(a) as cnt from Application a where a.jobPosting.id = :postingId group by a.stage")
    List<StageCount> countByStageForPosting(@org.springframework.data.repository.query.Param("postingId") UUID postingId);

    interface StageCount {
        com.vikisol.arena.applications.entity.ApplicationStage getStage();
        long getCnt();
    }
    Optional<Application> findByCandidateIdAndJobPostingId(UUID candidateId, UUID jobPostingId);
    boolean existsByCandidateIdAndJobPostingId(UUID candidateId, UUID jobPostingId);
    boolean existsByCandidateIdAndJobPostingEnterpriseId(UUID candidateId, UUID enterpriseId);
    java.util.List<Application> findByCandidate(CandidateProfile candidate);
    java.util.List<Application> findByJobPosting(JobPosting jobPosting);

    // ARCHITECT-REVIEW-BE-1 SHOULD-FIX (business): JobPostingService.setStatus only needs to
    // know whether anyone has applied, not load every Application row to check .isEmpty().
    boolean existsByJobPostingId(java.util.UUID jobPostingId);

    // Batched form of existsByCandidateIdAndJobPostingEnterpriseId for a whole page of candidates
    // at once - one query instead of one-per-row. Callers turn this into a Set for O(1) lookups.
    @Query("select distinct a.candidate.id from Application a where a.candidate.id in :candidateIds and a.jobPosting.enterprise.id = :enterpriseId")
    List<UUID> findCandidateIdsWithApplicationToEnterprise(@Param("candidateIds") List<UUID> candidateIds, @Param("enterpriseId") UUID enterpriseId);

    // G21 as reworked by the app flow (§6): pay is shared with an employer only on a live
    // application to them where the person ticked "include my CTC".
    @Query("""
            select count(a) > 0 from Application a where a.candidate.id = :candidateId and a.jobPosting.enterprise.id = :enterpriseId
              and a.includeCtc = true and a.stage <> com.vikisol.arena.applications.entity.ApplicationStage.WITHDRAWN""")
    boolean ctcSharedWithEnterprise(@Param("candidateId") UUID candidateId, @Param("enterpriseId") UUID enterpriseId);

    @Query("""
            select distinct a.candidate.id from Application a where a.candidate.id in :candidateIds and a.jobPosting.enterprise.id = :enterpriseId
              and a.includeCtc = true and a.stage <> com.vikisol.arena.applications.entity.ApplicationStage.WITHDRAWN""")
    List<UUID> findCandidateIdsSharingCtcWithEnterprise(@Param("candidateIds") List<UUID> candidateIds, @Param("enterpriseId") UUID enterpriseId);

    // DemoContentService.removeAll() - the (unlikely but possible) case of a real candidate
    // applying to a demo job posting during the review window. deleteByJobPostingId is keyed on
    // the FK, not this row's own demoContent flag, so it catches that too.
    void deleteByJobPostingId(UUID jobPostingId);
}
