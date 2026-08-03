package com.vikisol.arena.interviews.repository;

import com.vikisol.arena.interviews.entity.Interview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterviewRepository extends JpaRepository<Interview, UUID> {
    Optional<Interview> findByApplicationId(UUID applicationId);
    List<Interview> findByAssignedHiringManagerIdOrderByCreatedAtDesc(UUID hiringManagerUserId);
}
