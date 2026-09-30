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

    // Live postings (open or paused): the plan cap and the company page's count. Drafts and
    // closed postings don't count.
    long countByEnterpriseAndStatusIn(EnterpriseProfile enterprise, java.util.Collection<PostingStatus> statuses);

    // The public company page: everything except drafts, which only the team sees.
    Page<JobPosting> findByEnterpriseAndStatusNot(EnterpriseProfile enterprise, PostingStatus status, Pageable pageable);

    long countByStatus(PostingStatus status);

    // PERFORMANCE.md: search candidates narrowed in the database to jobs containing the query's
    // first word in any field search reads - title, description, place, company, skills - or
    // whose industry, type or remote flag spells it (the caller works those out). A superset of
    // what SearchText can match. `pattern` is '%word%', already lowercase.
    @org.springframework.data.jpa.repository.Query("""
            select j from JobPosting j join fetch j.enterprise e
            where j.status = :status
              and (lower(j.title) like :pattern or lower(j.description) like :pattern or lower(j.location) like :pattern
                   or lower(e.companyName) like :pattern or j.industry in :industries or j.employmentType in :types
                   or (:remote = true and j.remote = true)
                   or exists (select 1 from JobPosting j2 join j2.skills s where j2.id = j.id and lower(s) like :pattern))
            order by j.createdAt desc
            """)
    List<JobPosting> searchCandidates(@org.springframework.data.repository.query.Param("status") PostingStatus status,
                                      @org.springframework.data.repository.query.Param("pattern") String pattern,
                                      @org.springframework.data.repository.query.Param("industries") java.util.Collection<com.vikisol.arena.profile.entity.Industry> industries,
                                      @org.springframework.data.repository.query.Param("types") java.util.Collection<com.vikisol.arena.jobs.entity.EmploymentType> types,
                                      @org.springframework.data.repository.query.Param("remote") boolean remote,
                                      Pageable pageable);

    // Live-posting counts for a page of companies in one query (PERFORMANCE.md).
    @org.springframework.data.jpa.repository.Query("select j.enterprise.id, count(j) from JobPosting j where j.enterprise.id in :ids and j.status in :statuses group by j.enterprise.id")
    List<Object[]> countByEnterpriseIdsAndStatusIn(@org.springframework.data.repository.query.Param("ids") java.util.Collection<UUID> ids,
                                                   @org.springframework.data.repository.query.Param("statuses") java.util.Collection<PostingStatus> statuses);

    // Batched warm-up for the `skills` @ElementCollection - called once per page of results with
    // all their ids, instead of one lazy-load query per posting. Deliberately not folded into the
    // @EntityGraph above: combining a collection fetch with Pageable makes Hibernate paginate in
    // memory instead of in SQL (see CandidateProfileRepository.search() for the same reasoning).
    @EntityGraph(attributePaths = "skills")
    @Query("select j from JobPosting j where j.id in :ids")
    List<JobPosting> findByIdInFetchingSkills(@Param("ids") List<UUID> ids);

    // DemoContentService - see PostRepository.findByDemoContentTrue()'s own comment.
    List<JobPosting> findByDemoContentTrue();
}
