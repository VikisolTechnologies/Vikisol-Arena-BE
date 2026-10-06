package com.vikisol.arena.activities.repository;

import com.vikisol.arena.activities.entity.ActivityFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ActivityFeedbackRepository extends JpaRepository<ActivityFeedback, UUID> {

    Optional<ActivityFeedback> findByPostIdAndFromUserIdAndToUserId(UUID postId, UUID fromUserId, UUID toUserId);

    @EntityGraph(attributePaths = {"post", "fromUser"})
    Page<ActivityFeedback> findByToUserIdOrderByCreatedAtDescIdDesc(UUID toUserId, Pageable pageable);
}
