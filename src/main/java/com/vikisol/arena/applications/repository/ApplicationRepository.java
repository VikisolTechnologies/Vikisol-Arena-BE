package com.vikisol.arena.applications.repository;

import com.vikisol.arena.applications.entity.Application;
import com.vikisol.arena.jobs.entity.JobPosting;
import com.vikisol.arena.profile.entity.CandidateProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {
    Page<Application> findByCandidateId(UUID candidateId, Pageable pageable);
    Page<Application> findByJobPostingId(UUID jobPostingId, Pageable pageable);
    Optional<Application> findByCandidateIdAndJobPostingId(UUID candidateId, UUID jobPostingId);
    boolean existsByCandidateIdAndJobPostingId(UUID candidateId, UUID jobPostingId);
    long countByJobPostingEnterpriseId(UUID enterpriseId);
    boolean existsByCandidateIdAndJobPostingEnterpriseId(UUID candidateId, UUID enterpriseId);
    java.util.List<Application> findByCandidate(CandidateProfile candidate);
    java.util.List<Application> findByJobPosting(JobPosting jobPosting);
}
