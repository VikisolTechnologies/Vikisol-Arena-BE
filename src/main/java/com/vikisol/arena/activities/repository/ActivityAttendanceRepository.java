package com.vikisol.arena.activities.repository;

import com.vikisol.arena.activities.entity.ActivityAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityAttendanceRepository extends JpaRepository<ActivityAttendance, UUID> {
    Optional<ActivityAttendance> findByJoinRequestId(UUID joinId);

    List<ActivityAttendance> findByJoinRequestIdIn(List<UUID> joinIds);

    // Flow §9 disputes queue, oldest first.
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"joinRequest", "joinRequest.user", "joinRequest.post", "joinRequest.post.authorUser"})
    org.springframework.data.domain.Page<ActivityAttendance> findByDisputeStatusOrderByDisputedAtAscIdAsc(
            com.vikisol.arena.activities.entity.DisputeStatus status, org.springframework.data.domain.Pageable pageable);

    // FE-API-GAPS row 46: open disputes past the 72-hour review window.
    org.springframework.data.domain.Page<com.vikisol.arena.activities.entity.ActivityAttendance> findByDisputeStatusAndDisputedAtBeforeOrderByDisputedAtAscIdAsc(
            com.vikisol.arena.activities.entity.DisputeStatus status, java.time.Instant before, org.springframework.data.domain.Pageable pageable);

}
