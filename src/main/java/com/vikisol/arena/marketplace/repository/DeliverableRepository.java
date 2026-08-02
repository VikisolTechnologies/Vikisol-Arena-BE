package com.vikisol.arena.marketplace.repository;

import com.vikisol.arena.marketplace.entity.Deliverable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeliverableRepository extends JpaRepository<Deliverable, UUID> {
    List<Deliverable> findByMilestoneIdOrderBySubmittedAtDesc(UUID milestoneId);
}
