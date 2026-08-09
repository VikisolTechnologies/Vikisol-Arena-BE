package com.vikisol.arena.posts.service;

import com.vikisol.arena.follows.repository.FollowRepository;
import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.entity.PostStatus;
import com.vikisol.arena.posts.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * ARENA-V2-PRODUCT-ARCHITECTURE.md §7.3: "recency decay x affinity(follows) x proximity x
 * relevance x urgency x quality." Phase A implements recency + follows-affinity only - proximity
 * needs geo (Phase B, not built yet) and relevance/quality are embedding/reports-based (Phase C).
 * No single SQL ORDER BY expresses a composite score like this cleanly, so this mirrors
 * ScoringService's own style: fetch a bounded window, score in Java, sort, paginate - same
 * "fetch then compute" shape as match-percentage/career-health, not a new pattern.
 */
@Service
@RequiredArgsConstructor
public class FeedRankingService {

    // Bounded window, not the whole table - keeps this a fixed-cost read regardless of how many
    // posts exist. Revisit with a real windowing/cursor strategy if Phase A's post volume ever
    // approaches this in practice; not worth building pagination-over-a-score for a first pass.
    private static final int FEED_WINDOW_SIZE = 500;
    private static final double FOLLOW_BONUS = 25.0;
    private static final double RECENCY_HALF_LIFE_HOURS = 18.0;

    private final PostRepository postRepository;
    private final FollowRepository followRepository;

    @Transactional(readOnly = true)
    public List<Post> getFeedWindow(UUID viewingUserId, int page, int size) {
        Pageable window = PageRequest.of(0, FEED_WINDOW_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Post> candidates = postRepository.findByStatusOrderByCreatedAtDesc(PostStatus.OPEN, window).getContent();

        Set<UUID> following = viewingUserId == null ? Set.of()
                : Set.copyOf(followRepository.findFollowingUserIdsByFollowerUserId(viewingUserId));

        List<Post> scored = candidates.stream()
                .sorted(Comparator.comparingDouble((Post p) -> score(p, following)).reversed())
                .toList();

        int from = Math.min(page * size, scored.size());
        int to = Math.min(from + size, scored.size());
        return scored.subList(from, to);
    }

    private double score(Post post, Set<UUID> following) {
        double hoursOld = Duration.between(post.getCreatedAt(), Instant.now()).toMinutes() / 60.0;
        double recencyScore = 100.0 * Math.pow(0.5, hoursOld / RECENCY_HALF_LIFE_HOURS);
        double followBonus = following.contains(post.getAuthorUser().getId()) ? FOLLOW_BONUS : 0.0;
        // Proximity term: no-op this phase - hook point for Phase B once posts carry real geo.
        double proximityScore = 0.0;
        return recencyScore + followBonus + proximityScore;
    }
}
