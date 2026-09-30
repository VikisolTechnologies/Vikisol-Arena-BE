package com.vikisol.arena.activities.repository;

import com.vikisol.arena.activities.entity.PostReminder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PostReminderRepository extends JpaRepository<PostReminder, UUID> {

    List<PostReminder> findByPostIdAndUserId(UUID postId, UUID userId);

    List<PostReminder> findByPostIdAndSentAtIsNull(UUID postId);

    List<PostReminder> findByUserId(UUID userId);

    @EntityGraph(attributePaths = {"post", "user"})
    List<PostReminder> findTop200BySentAtIsNullAndRemindAtLessThanEqualOrderByRemindAtAsc(Instant now);
}
