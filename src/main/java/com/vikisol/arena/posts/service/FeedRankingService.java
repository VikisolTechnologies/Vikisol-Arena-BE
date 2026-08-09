package com.vikisol.arena.posts.service;

import com.vikisol.arena.common.embedding.EmbeddingProvider;
import com.vikisol.arena.common.embedding.EmbeddingUtil;
import com.vikisol.arena.follows.repository.FollowRepository;
import com.vikisol.arena.platform.entity.ModerationContentType;
import com.vikisol.arena.platform.repository.ModerationItemRepository;
import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.entity.PostStatus;
import com.vikisol.arena.posts.repository.PostCommentRepository;
import com.vikisol.arena.posts.repository.PostReactionRepository;
import com.vikisol.arena.posts.repository.PostRepository;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ARENA-V2-PRODUCT-ARCHITECTURE.md §7.3: "recency decay x affinity(follows) x proximity x
 * relevance x urgency x quality." Phase B added the proximity term (see {@code getFeedWindow}'s
 * own note - it's still a no-op hook here since proximity actually lives in
 * {@code PostService.getNearby}'s own dedicated radius search, not blended into the general
 * feed's score; the map/nearby screen and the feed are deliberately two different views over the
 * same posts). Phase C adds relevance (embedding cosine similarity) and quality (inverse of
 * live report count) - see DECISIONS.md for both. No single SQL ORDER BY expresses a composite
 * score like this cleanly, so this mirrors ScoringService's own style: fetch a bounded window,
 * score in Java, sort, paginate - same "fetch then compute" shape as match-percentage/
 * career-health, not a new pattern.
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
    private static final double RELEVANCE_WEIGHT = 40.0;
    private static final double QUALITY_PENALTY_PER_REPORT = 15.0;
    private static final int TRENDING_WINDOW_HOURS = 48;

    private final PostRepository postRepository;
    private final FollowRepository followRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final EmbeddingProvider embeddingProvider;
    private final ModerationItemRepository moderationItemRepository;
    private final PostCommentRepository postCommentRepository;
    private final PostReactionRepository postReactionRepository;

    @Transactional(readOnly = true)
    public List<Post> getFeedWindow(UUID viewingUserId, int page, int size) {
        Pageable window = PageRequest.of(0, FEED_WINDOW_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Post> candidates = postRepository.findByStatusOrderByCreatedAtDesc(PostStatus.OPEN, window).getContent();

        Set<UUID> following = viewingUserId == null ? Set.of()
                : Set.copyOf(followRepository.findFollowingUserIdsByFollowerUserId(viewingUserId));
        float[] interestVector = interestVectorFor(viewingUserId);
        Map<UUID, Long> reportCounts = batchReportCounts(candidates);

        List<Post> scored = candidates.stream()
                .sorted(Comparator.comparingDouble((Post p) -> score(p, following, interestVector, reportCounts)).reversed())
                .toList();

        return page(scored, page, size);
    }

    // §"trends" (Phase C) - same bounded-window-then-score approach, but ranked by recent
    // engagement velocity instead of the feed's recency/relevance blend. Deliberately reuses
    // total engagement counts (not just "in the last 48h") since neither PostComment nor
    // PostReaction call sites need per-event windowing elsewhere yet - see DECISIONS.md for why
    // this simplification is still an honest "trending" signal (recency decay on the post's own
    // createdAt already keeps genuinely old posts from dominating regardless).
    @Transactional(readOnly = true)
    public List<Post> getTrendingWindow(UUID viewingUserId, int page, int size) {
        Instant since = Instant.now().minus(Duration.ofDays(7));
        Pageable window = PageRequest.of(0, FEED_WINDOW_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Post> candidates = postRepository.findByStatusOrderByCreatedAtDesc(PostStatus.OPEN, window).getContent()
                .stream().filter(p -> p.getCreatedAt().isAfter(since)).toList();

        List<UUID> postIds = candidates.stream().map(Post::getId).toList();
        Map<UUID, Long> commentCounts = countMap(postCommentRepository.countByPostIdIn(postIds));
        Map<UUID, Long> reactionCounts = countMap(postReactionRepository.countByPostIdIn(postIds));

        List<Post> scored = candidates.stream()
                .sorted(Comparator.comparingDouble((Post p) -> trendingScore(p, commentCounts, reactionCounts)).reversed())
                .toList();
        return page(scored, page, size);
    }

    private double trendingScore(Post post, Map<UUID, Long> commentCounts, Map<UUID, Long> reactionCounts) {
        long joins = post.getSpotsFilled();
        long comments = commentCounts.getOrDefault(post.getId(), 0L);
        long reactions = reactionCounts.getOrDefault(post.getId(), 0L);
        double engagement = joins * 3.0 + comments * 2.0 + reactions * 1.0;
        double hoursOld = Duration.between(post.getCreatedAt(), Instant.now()).toMinutes() / 60.0;
        double recencyDecay = Math.pow(0.5, hoursOld / TRENDING_WINDOW_HOURS);
        return engagement * recencyDecay;
    }

    private List<Post> page(List<Post> scored, int page, int size) {
        int from = Math.min(page * size, scored.size());
        int to = Math.min(from + size, scored.size());
        return scored.subList(from, to);
    }

    private double score(Post post, Set<UUID> following, float[] interestVector, Map<UUID, Long> reportCounts) {
        double hoursOld = Duration.between(post.getCreatedAt(), Instant.now()).toMinutes() / 60.0;
        double recencyScore = 100.0 * Math.pow(0.5, hoursOld / RECENCY_HALF_LIFE_HOURS);
        double followBonus = following.contains(post.getAuthorUser().getId()) ? FOLLOW_BONUS : 0.0;
        // Proximity term: intentionally still 0 here - proximity is served by PostService's own
        // dedicated nearby/radius search (Map screen), not blended into this general feed score.
        double proximityScore = 0.0;
        double relevanceScore = interestVector == null ? 0.0
                : RELEVANCE_WEIGHT * EmbeddingUtil.cosineSimilarity(interestVector, EmbeddingUtil.decode(post.getEmbedding()));
        double qualityPenalty = reportCounts.getOrDefault(post.getId(), 0L) * QUALITY_PENALTY_PER_REPORT;
        return recencyScore + followBonus + proximityScore + relevanceScore - qualityPenalty;
    }

    // §7.3 "relevance" - embeds the viewer's own skills + bio + a handful of their most recent
    // post bodies into the same vector space posts are embedded in at creation time, so cosine
    // similarity is comparing like with like. Recomputed per feed request rather than cached on
    // the profile - see DECISIONS.md's "no premature caching" note; revisit if this shows up in
    // real latency numbers.
    private float[] interestVectorFor(UUID viewingUserId) {
        if (viewingUserId == null) return null;
        CandidateProfile profile = candidateProfileRepository.findByUserId(viewingUserId).orElse(null);
        if (profile == null) return null;
        StringBuilder text = new StringBuilder();
        profile.getSkills().forEach(s -> text.append(s.getName()).append(' '));
        if (profile.getBio() != null) text.append(profile.getBio()).append(' ');
        Pageable recentFew = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
        postRepository.findByAuthorUserIdOrderByCreatedAtDesc(viewingUserId, recentFew)
                .forEach(p -> text.append(p.getBody()).append(' '));
        return embeddingProvider.embed(text.toString());
    }

    private Map<UUID, Long> batchReportCounts(List<Post> posts) {
        List<UUID> postIds = posts.stream().map(Post::getId).toList();
        if (postIds.isEmpty()) return Map.of();
        return moderationItemRepository.countByRoomPostIdInAndContentType(postIds, ModerationContentType.ROOM).stream()
                .collect(Collectors.toMap(ModerationItemRepository.PostReportCountProjection::getPostId,
                        ModerationItemRepository.PostReportCountProjection::getCnt));
    }

    private Map<UUID, Long> countMap(List<PostCommentRepository.PostCountProjection> projections) {
        return projections.stream().collect(Collectors.toMap(
                PostCommentRepository.PostCountProjection::getPostId, PostCommentRepository.PostCountProjection::getCnt));
    }
}
