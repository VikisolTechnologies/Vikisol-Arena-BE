package com.vikisol.arena.posts.repository;

import com.vikisol.arena.posts.entity.PostJoinRequest;
import com.vikisol.arena.posts.entity.PostJoinStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostJoinRequestRepository extends JpaRepository<PostJoinRequest, UUID> {
    @EntityGraph(attributePaths = {"user", "post"})
    Page<PostJoinRequest> findByPostIdOrderByCreatedAtAscIdAsc(UUID postId, Pageable pageable);

    Optional<PostJoinRequest> findByPostIdAndUserId(UUID postId, UUID userId);

    Optional<PostJoinRequest> findByIdAndPostId(UUID id, UUID postId);

    long countByPostIdAndStatus(UUID postId, PostJoinStatus status);

    // Activity attendance sheet (ActivitiesService): everyone who joined, in the order they joined.
    @EntityGraph(attributePaths = "user")
    List<PostJoinRequest> findByPostIdAndStatusOrderByCreatedAtAscIdAsc(UUID postId, PostJoinStatus status);

    // PostService.delete() - a hard delete needs its dependents gone first (FK on post_id).
    void deleteByPostId(UUID postId);

    // §4 safety-audit fix: "Show join-count ... and account age" (a trust signal for whoever's
    // about to meet a stranger from an ACTIVITY/ASK post) - how many other posts this person has
    // actually been approved into elsewhere, a track record of real participation. Batched per
    // feed/nearby window, same shape as every other batch-count query in PostMapper.
    //
    // G11: a host's no-show only lowers this count once it is final - recorded before
    // `finalBefore` (72h ago) and not disputed. Until then, or while a dispute is open, it counts
    // like any other join, so host-recorded attendance can't become reputation unchecked.
    @Query("select j.user.id as userId, count(j) as cnt from PostJoinRequest j " +
            "where j.status = com.vikisol.arena.posts.entity.PostJoinStatus.APPROVED " +
            "and (j.outcome is null or j.outcome <> com.vikisol.arena.posts.entity.PostJoinOutcome.NO_SHOW " +
            "  or exists (select a.id from com.vikisol.arena.activities.entity.ActivityAttendance a where a.joinRequest = j " +
            "    and (a.disputeStatus <> com.vikisol.arena.activities.entity.DisputeStatus.NONE or a.outcomeRecordedAt > :finalBefore))) " +
            "and j.user.id in :userIds group by j.user.id")
    List<UserJoinCountProjection> countApprovedByUserIdIn(@Param("userIds") List<UUID> userIds,
                                                         @Param("finalBefore") java.time.Instant finalBefore);

    // G32 "Joined": activities someone was approved into, with the same no-show rule as the trust
    // signal above (a no-show only drops out once final and undisputed).
    @Query("select count(j) from PostJoinRequest j " +
            "where j.user.id = :userId and j.status = com.vikisol.arena.posts.entity.PostJoinStatus.APPROVED " +
            "and j.post.intentType = com.vikisol.arena.posts.entity.PostIntentType.ACTIVITY " +
            "and (j.outcome is null or j.outcome <> com.vikisol.arena.posts.entity.PostJoinOutcome.NO_SHOW " +
            "  or exists (select a.id from com.vikisol.arena.activities.entity.ActivityAttendance a where a.joinRequest = j " +
            "    and (a.disputeStatus <> com.vikisol.arena.activities.entity.DisputeStatus.NONE or a.outcomeRecordedAt > :finalBefore)))")
    long countJoinedActivities(@Param("userId") UUID userId, @Param("finalBefore") java.time.Instant finalBefore);

    interface UserJoinCountProjection {
        UUID getUserId();
        long getCnt();
    }
}
