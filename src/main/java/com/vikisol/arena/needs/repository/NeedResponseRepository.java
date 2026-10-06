package com.vikisol.arena.needs.repository;

import com.vikisol.arena.needs.entity.NeedResponse;
import com.vikisol.arena.needs.entity.ResponseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NeedResponseRepository extends JpaRepository<NeedResponse, UUID> {

    Optional<NeedResponse> findByPostIdAndUserId(UUID postId, UUID userId);

    Optional<NeedResponse> findByIdAndPostId(UUID id, UUID postId);

    // The owner's list: oldest first, like join requests.
    @EntityGraph(attributePaths = "user")
    List<NeedResponse> findByPostIdAndStatusNotOrderByCreatedAtAscIdAsc(UUID postId, ResponseStatus excluded);

    long countByPostIdAndStatusNot(UUID postId, ResponseStatus excluded);

    // Row 27: an offer's limit counts requests accepted since the start of its window.
    long countByPostIdAndStatusAndDecidedAtAfter(UUID postId, ResponseStatus status, java.time.Instant since);

    // Row 38: offers of help on a window of feed needs, oldest first, for counts and faces.
    @EntityGraph(attributePaths = "user")
    List<NeedResponse> findByPostIdInAndStatusInOrderByCreatedAtAscIdAsc(java.util.Collection<UUID> postIds, java.util.Collection<ResponseStatus> statuses);

    // "My offers": what I responded to, newest first.
    @EntityGraph(attributePaths = "post")
    Page<NeedResponse> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId, Pageable pageable);
}
