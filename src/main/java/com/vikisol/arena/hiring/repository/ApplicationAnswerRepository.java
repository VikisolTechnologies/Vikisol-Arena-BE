package com.vikisol.arena.hiring.repository;

import com.vikisol.arena.hiring.entity.ApplicationAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApplicationAnswerRepository extends JpaRepository<ApplicationAnswer, UUID> {
    List<ApplicationAnswer> findByApplicationId(UUID applicationId);

    boolean existsByQuestionPostingId(UUID postingId);
}
