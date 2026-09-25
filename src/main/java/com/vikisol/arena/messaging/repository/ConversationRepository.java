package com.vikisol.arena.messaging.repository;

import com.vikisol.arena.messaging.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    @Query("select c from Conversation c where c.userA.id = :userId or c.userB.id = :userId order by c.lastMessageAt desc")
    List<Conversation> findAllForUser(@Param("userId") UUID userId);

    @Query("""
            select c from Conversation c
            where ((c.userA.id = :userId1 and c.userB.id = :userId2)
               or (c.userA.id = :userId2 and c.userB.id = :userId1))
              and c.anonymousA = false and c.anonymousB = false
            """)
    Optional<Conversation> findBetween(@Param("userId1") UUID userId1, @Param("userId2") UUID userId2);

    // Phase 2 part C - every conversation between two people, anonymous ones included.
    @Query("""
            select c from Conversation c
            where (c.userA.id = :userId1 and c.userB.id = :userId2)
               or (c.userA.id = :userId2 and c.userB.id = :userId1)
            """)
    List<Conversation> findAllBetween(@Param("userId1") UUID userId1, @Param("userId2") UUID userId2);

    // Rate limit: anonymous chats a user started (they're always side A) since a moment.
    @Query("select count(c) from Conversation c where c.userA.id = :userId and (c.anonymousA = true or c.anonymousB = true) and c.createdAt > :since")
    long countAnonymousStartedSince(@Param("userId") UUID userId, @Param("since") java.time.Instant since);
}
