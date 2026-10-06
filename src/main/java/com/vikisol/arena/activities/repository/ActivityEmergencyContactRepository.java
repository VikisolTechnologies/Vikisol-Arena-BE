package com.vikisol.arena.activities.repository;

import com.vikisol.arena.activities.entity.ActivityEmergencyContact;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityEmergencyContactRepository extends JpaRepository<ActivityEmergencyContact, UUID> {

    Optional<ActivityEmergencyContact> findByPostIdAndUserId(UUID postId, UUID userId);

    @EntityGraph(attributePaths = "user")
    List<ActivityEmergencyContact> findByPostId(UUID postId);

    List<ActivityEmergencyContact> findByUserId(UUID userId);

    // After the trek: its end, or a day after the start when it has no end.
    @Modifying
    @Query("""
            delete from ActivityEmergencyContact c where c.post.id in (
                select p.id from Post p where (p.endsAt is not null and p.endsAt < :now)
                   or (p.endsAt is null and p.startsAt is not null and p.startsAt < :dayAgo))
            """)
    int deleteFinished(@Param("now") Instant now, @Param("dayAgo") Instant dayAgo);
}
