package com.vikisol.arena.hiring.repository;

import com.vikisol.arena.hiring.entity.ScreeningQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScreeningQuestionRepository extends JpaRepository<ScreeningQuestion, UUID> {
    List<ScreeningQuestion> findByPostingIdOrderByPositionAsc(UUID postingId);

    void deleteByPostingId(UUID postingId);
}
