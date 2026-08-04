package com.vikisol.arena.platform.repository;

import com.vikisol.arena.platform.entity.ModerationItem;
import com.vikisol.arena.platform.entity.ModerationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ModerationItemRepository extends JpaRepository<ModerationItem, UUID> {
    Page<ModerationItem> findByStatusOrderByCreatedAtDesc(ModerationStatus status, Pageable pageable);
    long countByStatus(ModerationStatus status);
}
