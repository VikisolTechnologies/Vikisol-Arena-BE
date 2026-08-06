package com.vikisol.arena.marketplace.repository;

import com.vikisol.arena.marketplace.entity.Bid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BidRepository extends JpaRepository<Bid, UUID> {
    @EntityGraph(attributePaths = "bidderUser")
    List<Bid> findByProjectIdOrderByAmountDesc(UUID projectId);

    // Batched form of the above for a whole page of projects at once - one query instead of
    // one-per-project. Callers group the result by project id in Java.
    @EntityGraph(attributePaths = "bidderUser")
    @Query("select b from Bid b where b.project.id in :projectIds order by b.amount desc")
    List<Bid> findByProjectIdInOrderByAmountDesc(@Param("projectIds") List<UUID> projectIds);

    @EntityGraph(attributePaths = "bidderUser")
    Page<Bid> findByBidderUserIdOrderBySubmittedAtDesc(UUID userId, Pageable pageable);
}
