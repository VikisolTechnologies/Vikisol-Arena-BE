package com.vikisol.arena.applications.repository;

import com.vikisol.arena.applications.entity.ApplicationNote;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApplicationNoteRepository extends JpaRepository<ApplicationNote, UUID> {
    @EntityGraph(attributePaths = "author")
    List<ApplicationNote> findByApplicationIdOrderByCreatedAtDescIdDesc(UUID applicationId);

    List<ApplicationNote> findByAuthorId(UUID authorUserId);
}
