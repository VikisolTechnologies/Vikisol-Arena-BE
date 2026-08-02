package com.vikisol.arena.marketplace.repository;

import com.vikisol.arena.marketplace.entity.Bid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BidRepository extends JpaRepository<Bid, UUID> {
    List<Bid> findByProjectIdOrderByAmountDesc(UUID projectId);
    Page<Bid> findByBidderUserIdOrderBySubmittedAtDesc(UUID userId, Pageable pageable);
}
