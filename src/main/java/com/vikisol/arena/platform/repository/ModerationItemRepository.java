package com.vikisol.arena.platform.repository;

import com.vikisol.arena.platform.entity.ModerationContentType;
import com.vikisol.arena.platform.entity.ModerationItem;
import com.vikisol.arena.platform.entity.ModerationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ModerationItemRepository extends JpaRepository<ModerationItem, UUID> {
    Page<ModerationItem> findByStatusOrderByCreatedAtDesc(ModerationStatus status, Pageable pageable);
    long countByStatus(ModerationStatus status);

    // FeedRankingService's §7.3 "quality" term (Phase C) - a post's live report count via its
    // Room (only ACTIVITY/ASK posts ever have one; UPDATE posts get a neutral quality score,
    // see DECISIONS.md). Batched for a whole feed window, same shape as every other batch-count
    // query in this codebase.
    @Query("select m.room.post.id as postId, count(m) as cnt from ModerationItem m " +
            "where m.contentType = :type and m.room.post.id in :postIds group by m.room.post.id")
    List<PostReportCountProjection> countByRoomPostIdInAndContentType(
            @Param("postIds") List<UUID> postIds, @Param("type") ModerationContentType type);

    interface PostReportCountProjection {
        UUID getPostId();
        long getCnt();
    }
}
