package com.vikisol.arena.activity.repository;

import com.vikisol.arena.activity.entity.ActivityEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ActivityEventRepository extends JpaRepository<ActivityEvent, UUID> {
    Page<ActivityEvent> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
