package com.vikisol.arena.activities.repository;

import com.vikisol.arena.activities.entity.ActivityDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ActivityDetailsRepository extends JpaRepository<ActivityDetails, UUID> {
    Optional<ActivityDetails> findByPostId(UUID postId);
}
