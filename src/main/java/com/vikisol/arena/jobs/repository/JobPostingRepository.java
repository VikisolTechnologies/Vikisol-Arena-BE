package com.vikisol.arena.jobs.repository;

import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.jobs.entity.JobPosting;
import com.vikisol.arena.jobs.entity.PostingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface JobPostingRepository extends JpaRepository<JobPosting, UUID> {
    // `enterprise` is a single-valued (@ManyToOne) association, so fetching it eagerly alongside
    // Pageable is safe - unlike `skills` below (an @ElementCollection), it can't multiply rows and
    // doesn't force Hibernate into in-memory pagination.
    @EntityGraph(attributePaths = "enterprise")
    Page<JobPosting> findByStatus(PostingStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "enterprise")
    Page<JobPosting> findByEnterprise(EnterpriseProfile enterprise, Pageable pageable);

    // "Active" = anything that isn't closed (open or paused) - matches arena-web's createPosting()
    // limit check in enterprise.ts exactly (`readPostings().filter((p) => p.status !== "closed")`).
    long countByEnterpriseAndStatusNot(EnterpriseProfile enterprise, PostingStatus status);

    long countByStatus(PostingStatus status);

    // Batched warm-up for the `skills` @ElementCollection - called once per page of results with
    // all their ids, instead of one lazy-load query per posting. Deliberately not folded into the
    // @EntityGraph above: combining a collection fetch with Pageable makes Hibernate paginate in
    // memory instead of in SQL (see CandidateProfileRepository.search() for the same reasoning).
    @EntityGraph(attributePaths = "skills")
    @Query("select j from JobPosting j where j.id in :ids")
    List<JobPosting> findByIdInFetchingSkills(@Param("ids") List<UUID> ids);
}
