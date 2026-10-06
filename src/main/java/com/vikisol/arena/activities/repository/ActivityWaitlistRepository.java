package com.vikisol.arena.activities.repository;

import com.vikisol.arena.activities.entity.ActivityWaitlistEntry;
import com.vikisol.arena.activities.entity.WaitlistStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityWaitlistRepository extends JpaRepository<ActivityWaitlistEntry, UUID> {

    Optional<ActivityWaitlistEntry> findByPostIdAndUserId(UUID postId, UUID userId);

    // The queue, first come first served; id breaks same-instant ties.
    @EntityGraph(attributePaths = "user")
    List<ActivityWaitlistEntry> findByPostIdAndStatusOrderByJoinedAtAscIdAsc(UUID postId, WaitlistStatus status);

    long countByPostIdAndStatus(UUID postId, WaitlistStatus status);

    // A person's position is 1 + how many are ahead of them.
    long countByPostIdAndStatusAndJoinedAtBefore(UUID postId, WaitlistStatus status, Instant joinedAt);
}
