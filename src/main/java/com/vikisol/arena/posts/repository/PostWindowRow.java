package com.vikisol.arena.posts.repository;

import com.vikisol.arena.posts.entity.PostIntentType;

import java.time.Instant;
import java.util.UUID;

/** The fields feed and trending ranking read from a post (PostRepository.findWindowRows) - PERFORMANCE.md. */
public record PostWindowRow(UUID id, Instant createdAt, UUID authorId, UUID companyId, PostIntentType intentType,
                            boolean anonymous, boolean linkOnly, String embedding, Instant startsAt,
                            Integer capacity, int spotsFilled) {
}
