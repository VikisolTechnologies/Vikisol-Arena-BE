package com.vikisol.arena.activities.repository;

import com.vikisol.arena.activities.entity.ActivityQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ActivityQuestionRepository extends JpaRepository<ActivityQuestion, UUID> {
    List<ActivityQuestion> findByPostIdOrderByPositionAsc(UUID postId);

    // PostService.requestJoin: the plain join endpoint can't carry answers.
    boolean existsByPostIdAndRequiredTrue(UUID postId);

    void deleteByPostId(UUID postId);
}
