package com.vikisol.arena.hiring.repository;

import com.vikisol.arena.hiring.entity.JobRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JobRequirementRepository extends JpaRepository<JobRequirement, UUID> {
    List<JobRequirement> findByPostingIdOrderByKindAscPositionAsc(UUID postingId);

    void deleteByPostingId(UUID postingId);
}
