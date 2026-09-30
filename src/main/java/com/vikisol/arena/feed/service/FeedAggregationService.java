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
import com.vikisol.arena.posts.dto.PostResponse;
import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.service.PostService;
import com.vikisol.arena.common.cache.FeedWindowCache;
import com.vikisol.arena.common.cache.TtlCache;
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
    private final com.vikisol.arena.needs.repository.NeedResponseRepository needResponseRepository;
    private final com.vikisol.arena.profile.repository.CandidateProfileRepository candidateProfileRepository;
    // PERFORMANCE.md: the open jobs and projects as ready-made items, shared by every request for a
    // few seconds (FeedWindowCache, cleared after any job or project write). Only the viewer's
    // follow bonus and the time-based terms are computed per request.
    private final TtlCache<List<JobBase>> jobWindow;
    private final TtlCache<List<ProjectBase>> projectWindow;

    private record JobBase(FeedItemResponse item, Instant createdAt, UUID companyId) {
    }

    private record ProjectBase(FeedItemResponse item, Instant createdAt, UUID authorId, Instant endsAt) {
    }

    public FeedAggregationService(PostService postService, JobPostingRepository jobPostingRepository,
                                  ProjectRepository projectRepository, BidRepository bidRepository,
                                  FollowRepository followRepository,
                                  com.vikisol.arena.needs.repository.NeedResponseRepository needResponseRepository,
                                  com.vikisol.arena.profile.repository.CandidateProfileRepository candidateProfileRepository,
                                  FeedWindowCache feedWindowCache) {
        this.postService = postService;
        this.jobPostingRepository = jobPostingRepository;
        this.projectRepository = projectRepository;
        this.bidRepository = bidRepository;
        this.followRepository = followRepository;
        this.needResponseRepository = needResponseRepository;
        this.candidateProfileRepository = candidateProfileRepository;
        this.jobWindow = feedWindowCache.newWindow();
        this.projectWindow = feedWindowCache.newWindow();
    }

    public record ScoredFeedItem(FeedItemResponse item, double score, UUID authorUserId, UUID authorCompanyId) {
    }

    // One ranked entry before mapping: a post (mapped only if it lands on the page) or an
    // already-built job/project item.
    private record Candidate(double score, UUID authorUserId, UUID authorCompanyId,
                             com.vikisol.arena.posts.service.FeedRankingService.Candidate post, FeedItemResponse item) {
    }

    // PERFORMANCE.md: rank everything cheaply, page, then map only the page's posts (in batches).
    // The ranking and the result are the same as before; the work per request is not.
    @Transactional(readOnly = true)
    public List<FeedItemResponse> getFeed(UUID viewingUserId, String tab, int page, int size) {
        Set<UUID> followedCompanies = viewingUserId == null ? Set.of()
                : Set.copyOf(followRepository.findFollowingCompanyIdsByFollowerUserId(viewingUserId));
        Set<UUID> followingUsers = viewingUserId == null ? Set.of()
                : Set.copyOf(followRepository.findFollowingUserIdsByFollowerUserId(viewingUserId));
        List<Candidate> items = new ArrayList<>();
        for (var sp : postService.getScoredFeedCandidates(viewingUserId)) {
            var p = sp.post();
            // An anonymous post never carries its author into the "following" filter.
            UUID author = p.anonymous() ? null : p.authorId();
            items.add(new Candidate(sp.score(), author, p.companyId(), p, null));
        }
        scoredJobs(followedCompanies).forEach(i -> items.add(new Candidate(i.score(), i.authorUserId(), i.authorCompanyId(), null, i.item())));
        scoredProjects(followingUsers).forEach(i -> items.add(new Candidate(i.score(), i.authorUserId(), i.authorCompanyId(), null, i.item())));

        List<Candidate> ranked = items.stream()
                .filter(i -> !"following".equals(tab)
                        || (i.authorUserId() != null && followingUsers.contains(i.authorUserId()))
                        || (i.authorCompanyId() != null && followedCompanies.contains(i.authorCompanyId())))
                .sorted(Comparator.comparingDouble(Candidate::score).reversed())
                .toList();
        List<Candidate> pageItems = page(ranked, page, size);

        var pagePosts = pageItems.stream().map(Candidate::post).filter(java.util.Objects::nonNull).toList();
        Map<UUID, PostResponse> mapped = postService.toResponses(pagePosts, viewingUserId).stream()
                .collect(Collectors.toMap(r -> UUID.fromString(r.id()), r -> r));
        Map<UUID, List<FeedItemResponse.OfferAvatar>> offers = offersOnNeeds(pagePosts.stream()
                .filter(p -> p.intentType() == com.vikisol.arena.posts.entity.PostIntentType.ASK)
                .map(com.vikisol.arena.posts.service.FeedRankingService.Candidate::id).toList());
        return pageItems.stream()
                .map(c -> c.post() == null ? c.item() : postItem(mapped.get(c.post().id()), offers))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private FeedItemResponse postItem(PostResponse r, Map<UUID, List<FeedItemResponse.OfferAvatar>> offers) {
        if (r == null) return null;
        String authorCompanyId = r.authorCompanyId();
        // PostMapper already swaps authorName/authorEmoji to the company's when the post is
        // company-authored (see its own comment) - just mirror those here.
        String authorCompanyName = authorCompanyId != null ? r.authorName() : null;
        String authorCompanyEmoji = authorCompanyId != null ? r.authorEmoji() : null;
        boolean ask = "ask".equals(r.intentType());
        List<FeedItemResponse.OfferAvatar> faces = ask ? offers.getOrDefault(UUID.fromString(r.id()), List.of()) : null;
        return new FeedItemResponse(
                r.id(), r.intentType(), r.authorUserId(), r.authorName(), r.authorEmoji(),
                authorCompanyId, authorCompanyName, authorCompanyEmoji,
                r.title(), r.body(), r.locationText(), r.tags(), r.mediaUrls(), r.status(), r.createdAt(),
                r.visibility(), r.capacity(), r.spotsFilled(), r.startsAt(), r.endsAt(), r.joinable(),
                r.mine(), r.myJoinStatus(), r.roomId(), r.approxLat(), r.approxLng(),
                r.commentCount(), r.reactionCount(), r.myReacted(),
                r.authorJoinCount(), r.authorAccountAgeDays(),
                null, null, null, null,
                null, null, null, null,
                r.demoContent(),
                r.priceInr(), r.authorVerificationLevel(),
                ask ? (long) faces.size() : null,
                ask ? faces.stream().limit(3).toList() : null
        );
    }

    // Row 38: live offers of help (pending or accepted) on the window's needs, oldest first. One
    // query for the responses and one for the profiles, whatever the window size.
    private Map<UUID, List<FeedItemResponse.OfferAvatar>> offersOnNeeds(List<UUID> needIds) {
        if (needIds.isEmpty()) return Map.of();
        var responses = needResponseRepository.findByPostIdInAndStatusInOrderByCreatedAtAscIdAsc(needIds,
                List.of(com.vikisol.arena.needs.entity.ResponseStatus.PENDING, com.vikisol.arena.needs.entity.ResponseStatus.ACCEPTED));
        var profiles = candidateProfileRepository.mapByUserId(responses.stream().map(r -> r.getUser().getId()).toList());
        Map<UUID, List<FeedItemResponse.OfferAvatar>> out = new java.util.HashMap<>();
        for (var r : responses) {
            var profile = profiles.get(r.getUser().getId());
            out.computeIfAbsent(r.getPost().getId(), k -> new ArrayList<>()).add(new FeedItemResponse.OfferAvatar(
                    profile != null ? profile.getName() : r.getUser().getName(),
                    profile != null ? profile.getAvatarEmoji() : "🧑🏽",
                    profile != null ? profile.getPhotoUrl() : null));
        }
        return out;
    }

    private List<ScoredFeedItem> scoredJobs(Set<UUID> followedCompanies) {
        return jobWindow.get(this::loadJobs).stream().map(j -> new ScoredFeedItem(j.item(),
                recencyScore(j.createdAt()) + (followedCompanies.contains(j.companyId()) ? FOLLOW_BONUS : 0.0),
                null, j.companyId())).toList();
    }

    private List<JobBase> loadJobs() {
        Pageable window = PageRequest.of(0, JOB_PROJECT_WINDOW, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<JobPosting> jobs = jobPostingRepository.findByStatus(PostingStatus.OPEN, window).getContent();
        // Cached items outlive this session: skills are loaded in one batch and copied out.
        if (!jobs.isEmpty()) jobPostingRepository.findByIdInFetchingSkills(jobs.stream().map(JobPosting::getId).toList());

        return jobs.stream().map(job -> {
            var company = job.getEnterprise();
            FeedItemResponse item = new FeedItemResponse(
                    job.getId().toString(), "job", null, null, null,
                    company.getId().toString(), company.getCompanyName(), company.getLogoEmoji(),
                    job.getTitle(), job.getDescription(), job.getLocation(), java.util.Collections.unmodifiableList(new ArrayList<>(job.getSkills())), List.of(),
                    job.getStatus().name().toLowerCase(), job.getCreatedAt().toString(),
                    null, null, null, null, null, null, null, null, null, null, null,
                    null, null, null,
                    null, null,
                    job.getEmploymentType().name().toLowerCase(), job.isRemote(), job.getSalaryMin(), job.getSalaryMax(),
                    null, null, null, null,
                    job.isDemoContent() || company.isDemoContent(),
                    null, null, null, null
            );
            return new JobBase(item, job.getCreatedAt(), company.getId());
        }).toList();
    }

    private List<ScoredFeedItem> scoredProjects(Set<UUID> followedAuthors) {
        return projectWindow.get(this::loadProjects).stream().map(p -> new ScoredFeedItem(p.item(),
                recencyScore(p.createdAt()) + (followedAuthors.contains(p.authorId()) ? FOLLOW_BONUS : 0.0)
                        + DEADLINE_URGENCY_WEIGHT * deadlineUrgency(p.endsAt()),
                p.authorId(), null)).toList();
    }

    private List<ProjectBase> loadProjects() {
        Pageable window = PageRequest.of(0, JOB_PROJECT_WINDOW, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Project> projects = projectRepository.findByStatus(ProjectStatus.OPEN, window).getContent();
        List<UUID> projectIds = projects.stream().map(Project::getId).toList();
        if (!projectIds.isEmpty()) projectRepository.findByIdInFetchingSkills(projectIds);
        Map<UUID, Long> bidCounts = bidRepository.findByProjectIdInOrderByAmountDesc(projectIds).stream()
                .collect(Collectors.groupingBy(b -> b.getProject().getId(), Collectors.counting()));

        return projects.stream().map(project -> {
            var author = project.getPostedByUser();
            FeedItemResponse item = new FeedItemResponse(
                    project.getId().toString(), project.getKind().wireValue(), author.getId().toString(),
                    author.getName(), "🧑🏽", null, null, null,
                    project.getTitle(), project.getDescription(), null, java.util.Collections.unmodifiableList(new ArrayList<>(project.getSkills())), List.of(),
                    project.getStatus().name().toLowerCase(), project.getCreatedAt().toString(),
                    null, null, null, null, project.getEndsAt().toString(), null, null, null, null, null, null,
                    null, null, null,
                    null, null,
                    null, null, null, null,
                    project.getBudgetMin(), project.getBudgetMax(), project.getDurationWeeks(),
                    bidCounts.getOrDefault(project.getId(), 0L),
                    project.isDemoContent() || author.isDemoContent(),
                    null, null, null, null
            );
            return new ProjectBase(item, project.getCreatedAt(), author.getId(), project.getEndsAt());
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

    private static <T> List<T> page(List<T> sorted, int page, int size) {
        int from = Math.min(page * size, sorted.size());
        int to = Math.min(from + size, sorted.size());
        return sorted.subList(from, to);
    }
}
