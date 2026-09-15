package com.vikisol.arena.marketplace.repository;

import com.vikisol.arena.marketplace.entity.Milestone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MilestoneRepository extends JpaRepository<Milestone, UUID> {
    List<Milestone> findByProjectIdOrderByOrderIndexAsc(UUID projectId);

    // DemoContentService.removeAll() - see BidRepository.deleteByProjectId's own comment.
    void deleteByProjectId(UUID projectId);
}
