package com.vikisol.arena.hiring.repository;

import com.vikisol.arena.hiring.entity.JobRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JobRequirementRepository extends JpaRepository<JobRequirement, UUID> {
    List<JobRequirement> findByPostingIdOrderByKindAscPositionAsc(UUID postingId);

    // Rows 22/28: must-haves and nice-to-haves for a page of jobs in one query.
    List<JobRequirement> findByPostingIdInOrderByKindAscPositionAsc(java.util.Collection<UUID> postingIds);

    void deleteByPostingId(UUID postingId);
}
