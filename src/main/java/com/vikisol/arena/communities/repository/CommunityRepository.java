package com.vikisol.arena.communities.repository;

import com.vikisol.arena.communities.entity.Community;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CommunityRepository extends JpaRepository<Community, UUID> {
    Optional<Community> findBySlug(String slug);

    boolean existsBySlug(String slug);

    long countByCreatedById(UUID userId);

    java.util.List<Community> findByDemoContentTrue();
}
