package com.vikisol.arena.needs.repository;

import com.vikisol.arena.needs.entity.NeedCompletion;
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

public interface NeedCompletionRepository extends JpaRepository<NeedCompletion, UUID> {

    Optional<NeedCompletion> findByResponseId(UUID responseId);

    List<NeedCompletion> findByResponseIdIn(Collection<UUID> responseIds);

    // A person's confirmed outcomes, on either side, newest first (their public profile).
    @EntityGraph(attributePaths = {"response", "response.post"})
    @Query(value = """
            select c from NeedCompletion c
            where c.completedAt is not null
              and (c.response.user.id = :userId or c.response.post.authorUser.id = :userId)
            order by c.completedAt desc, c.id desc
            """,
            countQuery = """
            select count(c) from NeedCompletion c
            where c.completedAt is not null
              and (c.response.user.id = :userId or c.response.post.authorUser.id = :userId)
            """)
    Page<NeedCompletion> findCompletedFor(@Param("userId") UUID userId, Pageable pageable);

    // G32 "Helped": confirmed outcomes where this person gave - answered a need, or owned the offer.
    @Query("""
            select count(c) from NeedCompletion c
            where c.completedAt is not null and (
                (c.response.user.id = :userId and c.response.post.intentType = com.vikisol.arena.posts.entity.PostIntentType.ASK)
             or (c.response.post.authorUser.id = :userId and c.response.post.intentType = com.vikisol.arena.posts.entity.PostIntentType.OFFER))
            """)
    long countHelped(@Param("userId") UUID userId);
}
