package com.vikisol.arena.marketplace.repository;

import com.vikisol.arena.marketplace.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RatingRepository extends JpaRepository<Rating, UUID> {
    List<Rating> findByProjectId(UUID projectId);
    List<Rating> findByToUserId(UUID userId);
}
