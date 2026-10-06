package com.vikisol.arena.activities.repository;

import com.vikisol.arena.activities.entity.ActivityAnswer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ActivityAnswerRepository extends JpaRepository<ActivityAnswer, UUID> {

    @EntityGraph(attributePaths = "question")
    @Query("select a from ActivityAnswer a where a.question.post.id = :postId and a.user.id = :userId order by a.question.position")
    List<ActivityAnswer> findForPostAndUser(@Param("postId") UUID postId, @Param("userId") UUID userId);

    boolean existsByQuestionPostId(UUID postId);
}
