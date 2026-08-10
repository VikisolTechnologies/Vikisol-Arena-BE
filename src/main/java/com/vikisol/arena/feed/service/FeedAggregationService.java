package com.vikisol.arena.feed.service;

import com.vikisol.arena.feed.dto.FeedItemResponse;
import com.vikisol.arena.follows.repository.FollowRepository;
import com.vikisol.arena.jobs.entity.JobPosting;
import com.vikisol.arena.jobs.entity.PostingStatus;
import com.vikisol.arena.jobs.repository.JobPostingRepository;
import com.vikisol.arena.marketplace.entity.Project;
import com.vikisol.arena.marketplace.entity.ProjectStatus;
import com.vikisol.arena.marketplace.repository.BidRepository;
import com.vikisol.arena.marketplace.repository.ProjectRepository;
import com.vikisol.arena.posts.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ARENA-MASTER-ARCHITECTURE.md PART 6/7.5 - the real implementation of the decision logged in
 * DECISIONS.md ("Step 3: the feed unifies JOB/PROJECT/FREELANCE/ACTIVITY/ASK/UPDATE at the API
 * response level"). Queries {@link com.vikisol.arena.posts.entity.Post},
 * {@link JobPosting}, and {@link Project} independently, maps each into the same
 * {@link FeedItemResponse} shape, and merges+ranks them into one stream. Nothing about how any
 * of the three underlying tables actually works changes - bidding stays on Project, the
 * pipeline stays on JobPosting, join/room stays on Post.
 * <p>
 * Job/Project don't have FeedRankingService's full scoring machinery (no per-item embedding,
 * no urgency term for jobs) - deliberately kept to a same-shape-but-simpler recency+follow score
 * using the SAME constants as FeedRankingService's own recency/follow terms, so all three
 * sources interleave believably instead of one category silently dominating because its score
 * scale happens to run hotter. A real documented limitation, not fake sophistication: Job/
 * Project relevance-to-viewer ranking is a fast-follow once they're embedded too (see
 * DECISIONS.md).
 */
@Service
@RequiredArgsConstructor
public class FeedAggregationService {

    private static final int JOB_PROJECT_WINDOW = 200;
    private static final double RECENCY_HALF_LIFE_HOURS = 18.0;
    private static final double FOLLOW_BONUS = 25.0;
    private static final double DEADLINE_URGENCY_WEIGHT = 20.0;
    private static final double DEADLINE_URGENCY_HORIZON_HOURS = 7 * 24.0;

    private final PostService postService;
    private final JobPostingRepository jobPostingRepository;
    private final ProjectRepository projectRepository;
    private final BidRepository bidRepository;
    private final FollowRepository followRepository;

    public record ScoredFeedItem(FeedItemResponse item, double score, UUID authorUserId, UUID authorCompanyId) {
    }

    @Transactional(readOnly = true)
    public List<FeedItemResponse> getFeed(UUID viewingUserId, String tab, int page, int size) {
        List<ScoredFeedItem> items = new ArrayList<>();
        items.addAll(scoredPosts(viewingUserId));
        items.addAll(scoredJobs(viewingUserId));
        items.addAll(scoredProjects(viewingUserId));

        if ("following".equals(tab)) {
            Set<UUID> followingUsers = viewingUserId == null ? Set.of()
                    : Set.copyOf(followRepository.findFollowingUserIdsByFollowerUserId(viewingUserId));
            items = items.stream()
                    .filter(i -> (i.authorUserId() != null && followingUsers.contains(i.authorUserId()))
                            || (i.authorCompanyId() != null && viewingUserId != null
                            && followRepository.existsByFollowerUserIdAndFollowingCompanyId(viewingUserId, i.authorCompanyId())))
                    .toList();
        }

        List<FeedItemResponse> sorted = items.stream()
                .sorted(Comparator.comparingDouble(ScoredFeedItem::score).reversed())
                .map(ScoredFeedItem::item)
                .toList();
        return page(sorted, page, size);
    }

    private List<ScoredFeedItem> scoredPosts(UUID viewingUserId) {
        return postService.getScoredFeed(viewingUserId).stream()
                .map(sp -> {
                    var r = sp.response();
                    UUID authorUserId = UUID.fromString(r.authorUserId());
                    UUID authorCompanyId = r.authorCompanyId() == null ? null : UUID.fromString(r.authorCompanyId());
                    // PostMapper already swaps authorName/authorEmoji to the company's when the
                    // post is company-authored (see its own comment) - just mirror those here.
                    String authorCompanyName = authorCompanyId != null ? r.authorName() : null;
                    String authorCompanyEmoji = authorCompanyId != null ? r.authorEmoji() : null;
                    FeedItemResponse item = new FeedItemResponse(
                            r.id(), r.intentType(), r.authorUserId(), r.authorName(), r.authorEmoji(),
                            r.authorCompanyId(), authorCompanyName, authorCompanyEmoji,
                            r.title(), r.body(), r.locationText(), r.tags(), r.mediaUrls(), r.status(), r.createdAt(),
                            r.visibility(), r.capacity(), r.spotsFilled(), r.startsAt(), r.endsAt(), r.joinable(),
                            r.mine(), r.myJoinStatus(), r.roomId(), r.approxLat(), r.approxLng(),
                            r.commentCount(), r.reactionCount(), r.myReacted(),
                            null, null, null, null,
                            null, null, null, null
                    );
                    return new ScoredFeedItem(item, sp.score(), authorUserId, authorCompanyId);
                })
                .toList();
    }

    private List<ScoredFeedItem> scoredJobs(UUID viewingUserId) {
        Pageable window = PageRequest.of(0, JOB_PROJECT_WINDOW, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<JobPosting> jobs = jobPostingRepository.findByStatus(PostingStatus.OPEN, window).getContent();
        Set<UUID> followedCompanies = viewingUserId == null ? Set.of() : jobs.stream()
                .map(j -> j.getEnterprise().getId()).distinct()
                .filter(id -> followRepository.existsByFollowerUserIdAndFollowingCompanyId(viewingUserId, id))
                .collect(Collectors.toSet());

        return jobs.stream().map(job -> {
            double recency = recencyScore(job.getCreatedAt());
            double followBonus = followedCompanies.contains(job.getEnterprise().getId()) ? FOLLOW_BONUS : 0.0;
            var company = job.getEnterprise();
            FeedItemResponse item = new FeedItemResponse(
                    job.getId().toString(), "job", null, null, null,
                    company.getId().toString(), company.getCompanyName(), company.getLogoEmoji(),
                    job.getTitle(), job.getDescription(), job.getLocation(), job.getSkills(), List.of(),
                    job.getStatus().name().toLowerCase(), job.getCreatedAt().toString(),
                    null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null,
                    job.getEmploymentType().name().toLowerCase(), job.isRemote(), job.getSalaryMin(), job.getSalaryMax(),
                    null, null, null, null
            );
            return new ScoredFeedItem(item, recency + followBonus, null, company.getId());
        }).toList();
    }

    private List<ScoredFeedItem> scoredProjects(UUID viewingUserId) {
        Pageable window = PageRequest.of(0, JOB_PROJECT_WINDOW, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Project> projects = projectRepository.findByStatus(ProjectStatus.OPEN, window).getContent();
        List<UUID> projectIds = projects.stream().map(Project::getId).toList();
        Map<UUID, Long> bidCounts = bidRepository.findByProjectIdInOrderByAmountDesc(projectIds).stream()
                .collect(Collectors.groupingBy(b -> b.getProject().getId(), Collectors.counting()));
        Set<UUID> followedAuthors = viewingUserId == null ? Set.of()
                : Set.copyOf(followRepository.findFollowingUserIdsByFollowerUserId(viewingUserId));

        return projects.stream().map(project -> {
            double recency = recencyScore(project.getCreatedAt());
            double followBonus = followedAuthors.contains(project.getPostedByUser().getId()) ? FOLLOW_BONUS : 0.0;
            double urgency = deadlineUrgency(project.getEndsAt());
            var author = project.getPostedByUser();
            FeedItemResponse item = new FeedItemResponse(
                    project.getId().toString(), project.getKind().wireValue(), author.getId().toString(),
                    author.getName(), "🧑🏽", null, null, null,
                    project.getTitle(), project.getDescription(), null, project.getSkills(), List.of(),
                    project.getStatus().name().toLowerCase(), project.getCreatedAt().toString(),
                    null, null, null, null, project.getEndsAt().toString(), null, null, null, null, null, null,
                    null, null, null,
                    null, null, null, null,
                    project.getBudgetMin(), project.getBudgetMax(), project.getDurationWeeks(),
                    bidCounts.getOrDefault(project.getId(), 0L)
            );
            return new ScoredFeedItem(item, recency + followBonus + DEADLINE_URGENCY_WEIGHT * urgency, author.getId(), null);
        }).toList();
    }

    private double recencyScore(Instant createdAt) {
        double hoursOld = Duration.between(createdAt, Instant.now()).toMinutes() / 60.0;
        return 100.0 * Math.pow(0.5, hoursOld / RECENCY_HALF_LIFE_HOURS);
    }

    private double deadlineUrgency(Instant endsAt) {
        if (endsAt == null) return 0.0;
        double hoursUntil = Duration.between(Instant.now(), endsAt).toMinutes() / 60.0;
        if (hoursUntil <= 0 || hoursUntil > DEADLINE_URGENCY_HORIZON_HOURS) return 0.0;
        return 1.0 - (hoursUntil / DEADLINE_URGENCY_HORIZON_HOURS);
    }

    private List<FeedItemResponse> page(List<FeedItemResponse> sorted, int page, int size) {
        int from = Math.min(page * size, sorted.size());
        int to = Math.min(from + size, sorted.size());
        return sorted.subList(from, to);
    }
}
