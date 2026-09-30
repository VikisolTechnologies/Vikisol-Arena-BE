package com.vikisol.arena.jobs.repository;

import com.vikisol.arena.jobs.entity.SavedJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedJobRepository extends JpaRepository<SavedJob, UUID> {

    Optional<SavedJob> findByUserIdAndPostingId(UUID userId, UUID postingId);

    @EntityGraph(attributePaths = {"posting", "posting.enterprise"})
    Page<SavedJob> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId, Pageable pageable);

    @Query("select s.posting.id from SavedJob s where s.user.id = :userId and s.posting.id in :postingIds")
    List<UUID> findSavedPostingIds(@Param("userId") UUID userId, @Param("postingIds") Collection<UUID> postingIds);

    List<SavedJob> findByUserId(UUID userId);
}
