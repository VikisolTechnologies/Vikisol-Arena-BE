package com.vikisol.arena.interviews.repository;

import com.vikisol.arena.interviews.entity.Interview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InterviewRepository extends JpaRepository<Interview, UUID> {
    Optional<Interview> findByApplicationId(UUID applicationId);

    // HM1 "my interviews" - was an unbounded List with 4 lazy loads per row
    // (application, application.candidate, application.jobPosting,
    // application.jobPosting.enterprise, all touched by toHiringManagerResponse). All four are
    // single-valued (@OneToOne/@ManyToOne) associations, so combining them in one @EntityGraph
    // with Pageable is safe - unlike a collection fetch, they can't multiply rows or force
    // Hibernate into in-memory pagination (see CandidateProfileRepository.search()'s comment for
    // that distinction). Sorting comes from the Pageable itself (see InterviewController), same
    // convention as ProjectRepository.findByStatus().
    @EntityGraph(attributePaths = {"application", "application.candidate", "application.jobPosting", "application.jobPosting.enterprise"})
    Page<Interview> findByAssignedHiringManagerId(UUID hiringManagerUserId, Pageable pageable);
}
