package com.vikisol.arena.posts.repository;

import com.vikisol.arena.posts.entity.PostAudience;
import com.vikisol.arena.posts.entity.PostIntentType;

import java.util.UUID;

/** The fields search reads from a post (PostRepository.searchRows) - PERFORMANCE.md. */
public record PostSearchRow(UUID id, PostIntentType intentType, PostAudience audience, boolean anonymous, boolean linkOnly,
                            UUID authorId, String authorName, String companyName,
                            String title, String body, String locationText) {
}
