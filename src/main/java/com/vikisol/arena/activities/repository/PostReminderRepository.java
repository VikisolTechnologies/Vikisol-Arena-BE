package com.vikisol.arena.activities.repository;

import com.vikisol.arena.activities.entity.PostReminder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PostReminderRepository extends JpaRepository<PostReminder, UUID> {

    List<PostReminder> findByPostIdAndUserId(UUID postId, UUID userId);

    List<PostReminder> findByPostIdAndSentAtIsNull(UUID postId);

    List<PostReminder> findByUserId(UUID userId);

    // ARCHITECT-REVIEW-BE-1 SHOULD-FIX: with more than one app instance, two replicas' schedulers
    // could both pick up the same due reminder and send it twice. Spring Data has no portable
    // "SKIP LOCKED" derived query, so this claims a batch of ids with a native FOR UPDATE SKIP
    // LOCKED first - a replica that loses the race to lock a row just skips it instead of
    // blocking - then sendDue() loads and processes only those ids.
    @Query(value = "select id from arena_post_reminders where sent_at is null and remind_at <= :now "
            + "order by remind_at asc limit 200 for update skip locked", nativeQuery = true)
    List<UUID> lockNextDueBatch(@Param("now") Instant now);

    @EntityGraph(attributePaths = {"post", "user"})
    List<PostReminder> findByIdIn(List<UUID> ids);
}
