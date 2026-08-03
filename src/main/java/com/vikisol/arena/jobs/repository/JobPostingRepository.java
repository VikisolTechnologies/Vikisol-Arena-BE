package com.vikisol.arena.jobs.repository;

import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.jobs.entity.JobPosting;
import com.vikisol.arena.jobs.entity.PostingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JobPostingRepository extends JpaRepository<JobPosting, UUID> {
    Page<JobPosting> findByStatus(PostingStatus status, Pageable pageable);
    Page<JobPosting> findByEnterprise(EnterpriseProfile enterprise, Pageable pageable);

    // "Active" = anything that isn't closed (open or paused) - matches arena-web's createPosting()
    // limit check in enterprise.ts exactly (`readPostings().filter((p) => p.status !== "closed")`).
    long countByEnterpriseAndStatusNot(EnterpriseProfile enterprise, PostingStatus status);
}
