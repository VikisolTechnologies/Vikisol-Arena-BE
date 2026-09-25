package com.vikisol.arena.posts.service;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.entity.VerificationLevel;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.common.embedding.EmbeddingProvider;
import com.vikisol.arena.common.embedding.EmbeddingUtil;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.common.geo.GeohashUtil;
import com.vikisol.arena.common.util.AgeUtil;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.service.EnterpriseProfileService;
import com.vikisol.arena.follows.repository.FollowRepository;
import com.vikisol.arena.follows.service.BlockService;
import com.vikisol.arena.notifications.service.NotificationService;
import com.vikisol.arena.platform.repository.ModerationItemRepository;
import com.vikisol.arena.platform.service.ModerationService;
import com.vikisol.arena.common.service.CloudinaryService;
import com.vikisol.arena.search.SearchText;
import com.vikisol.arena.posts.dto.CreateCompanyPostRequest;
import com.vikisol.arena.posts.dto.CreatePostRequest;
import com.vikisol.arena.posts.dto.PostJoinRequestResponse;
import com.vikisol.arena.posts.dto.PostResponse;
import com.vikisol.arena.posts.entity.*;
import com.vikisol.arena.posts.repository.PostCommentRepository;
import com.vikisol.arena.posts.repository.PostJoinRequestRepository;
import com.vikisol.arena.posts.repository.PostReactionRepository;
import com.vikisol.arena.posts.repository.PostRepository;
import com.vikisol.arena.posts.repository.PostSaveRepository;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import com.vikisol.arena.rooms.entity.Room;
import com.vikisol.arena.rooms.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    // How many of the newest live posts search looks through - see SearchText on why in-memory.
    private static final int SEARCH_WINDOW = 2000;

    private final PostRepository postRepository;
    private final PostJoinRequestRepository postJoinRequestRepository;
    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final FollowRepository followRepository;
    private final PostMapper mapper;
    private final FeedRankingService feedRankingService;
    private final RoomService roomService;
    private final NotificationService notificationService;
    private final BlockService blockService;
    private final EmbeddingProvider embeddingProvider;
    private final ModerationService moderationService;
    private final EnterpriseProfileService enterpriseProfileService;
    private final PostSaveRepository postSaveRepository;
    private final PostReactionRepository postReactionRepository;
    private final PostCommentRepository postCommentRepository;
    private final ModerationItemRepository moderationItemRepository;
    private final CloudinaryService cloudinaryService;

    @Transactional(readOnly = true)
    public List<PostResponse> getFeed(UUID viewingUserId, int page, int size) {
        List<Post> window = feedRankingService.getFeedWindow(viewingUserId, page, size);
        window = excludeBlocked(window, viewingUserId);
        return toResponseList(window, viewingUserId);
    }

    // A fully-mapped PostResponse paired with its FeedRankingService score - used by
    // FeedAggregationService to interleave posts with JobPosting/Project on one merged, ranked
    // stream (see DECISIONS.md's Step 3 entry).
    public record ScoredPostResponse(PostResponse response, double score) {
    }

    // Unpaged, scored+sorted feed posts, fully mapped (comment/reaction counts, join status,
    // room id - everything getFeed's own responses have). Deliberately bypasses getFeed's own
    // paging since the caller needs to merge-then-page across all three sources together, not
    // page each independently and then try to interleave already-paged pages.
    @Transactional(readOnly = true)
    public List<ScoredPostResponse> getScoredFeed(UUID viewingUserId) {
        List<FeedRankingService.ScoredPost> scored = feedRankingService.scoredWindow(viewingUserId);
        List<Post> posts = excludeBlocked(scored.stream().map(FeedRankingService.ScoredPost::post).toList(), viewingUserId);
        Map<UUID, Double> scoreByPostId = scored.stream()
                .collect(Collectors.toMap(sp -> sp.post().getId(), FeedRankingService.ScoredPost::score, (a, b) -> a));
        return toResponseList(posts, viewingUserId).stream()
                .map(r -> new ScoredPostResponse(r, scoreByPostId.getOrDefault(UUID.fromString(r.id()), 0.0)))
                .toList();
    }

    // §"trends" - trending posts ranked by recent engagement velocity, reused as a feed sort
    // option rather than a separate subsystem (see DECISIONS.md).
    @Transactional(readOnly = true)
    public List<PostResponse> getTrending(UUID viewingUserId, int page, int size) {
        List<Post> window = feedRankingService.getTrendingWindow(viewingUserId, page, size);
        window = excludeBlocked(window, viewingUserId);
        return toResponseList(window, viewingUserId);
    }

    @Transactional(readOnly = true)
    public PostResponse getPost(UUID id, UUID viewingUserId) {
        Post post = requirePost(id);
        return mapper.toResponse(post, viewingUserId, myJoinStatus(post, viewingUserId), roomIdFor(post));
    }

    @Transactional(readOnly = true)
    public PagedResponse<PostResponse> getMyPosts(UUID userId, Pageable pageable) {
        var page = postRepository.findByAuthorUserIdOrderByCreatedAtDesc(userId, pageable);
        var authorProfiles = batchAuthorProfiles(page.getContent());
        return PagedResponse.of(page,
                p -> mapper.toResponse(p, userId, myJoinStatus(p, userId), roomIdFor(p), authorProfiles));
    }

    // Profile-revamp "activity" tab (Phase C) - any user's own OPEN/FULL/CLOSED posts, respecting
    // each post's own audience gate the same way the feed itself would: GLOBAL always visible,
    // FOLLOWERS only visible to the target's own followers (or the target themself), CANCELLED
    // excluded (nothing to show a visitor about a post that never happened).
    @Transactional(readOnly = true)
    public PagedResponse<PostResponse> getUserPosts(UUID targetUserId, UUID viewingUserId, Pageable pageable) {
        boolean viewerFollowsTarget = viewingUserId != null
                && followRepository.existsByFollowerUserIdAndFollowingUserId(viewingUserId, targetUserId);
        boolean isSelf = viewingUserId != null && viewingUserId.equals(targetUserId);
        var page = postRepository.findByAuthorUserIdOrderByCreatedAtDesc(targetUserId, pageable);
        var visible = page.getContent().stream()
                .filter(p -> p.getStatus() != PostStatus.CANCELLED)
                .filter(p -> isSelf || p.getAudience() == PostAudience.GLOBAL
                        || (p.getAudience() == PostAudience.FOLLOWERS && viewerFollowsTarget))
                .toList();
        // Post-fetch audience filtering means totalElements/totalPages reflect the raw per-page
        // count, not a global count of visible-to-this-viewer posts - an accepted simplification
        // (see getUserPosts' own comment) rather than a second, more complex counting query.
        return new PagedResponse<>(toResponseList(visible, viewingUserId), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    // Global search (SearchService) - live posts matching every query word in their title, body,
    // place or author, best match first. Same visibility rules as a profile's post list: GLOBAL
    // posts for everyone, FOLLOWERS posts only for the author's followers (or the author), and
    // nothing from a blocked/blocking user.
    @Transactional(readOnly = true)
    public List<PostResponse> search(UUID viewingUserId, List<String> terms, java.util.function.Predicate<Post> kind, int limit) {
        if (terms.isEmpty()) return List.of();
        List<Post> window = postRepository.findByStatusInOrderByCreatedAtDesc(
                List.of(PostStatus.OPEN, PostStatus.FULL), PageRequest.of(0, SEARCH_WINDOW)).getContent();
        Set<UUID> following = viewingUserId == null ? Set.of()
                : Set.copyOf(followRepository.findFollowingUserIdsByFollowerUserId(viewingUserId));
        record Hit(Post post, int score) {
        }
        List<Post> ranked = window.stream()
                .filter(kind)
                .filter(p -> p.getAudience() == PostAudience.GLOBAL
                        || p.getAuthorUser().getId().equals(viewingUserId)
                        || following.contains(p.getAuthorUser().getId()))
                .map(p -> new Hit(p, SearchText.score(terms, p.getTitle(), SearchText.haystack(
                        p.getBody(), p.getLocationText(), p.getAuthorUser().getName(),
                        p.getAuthorCompany() == null ? null : p.getAuthorCompany().getCompanyName()))))
                .filter(h -> h.score() > 0)
                .sorted(java.util.Comparator.comparingInt(Hit::score).reversed())
                .map(Hit::post)
                .toList();
        List<Post> visible = excludeBlocked(ranked, viewingUserId);
        return toResponseList(visible.subList(0, Math.min(limit, visible.size())), viewingUserId);
    }

    private List<PostResponse> toResponseList(List<Post> posts, UUID viewingUserId) {
        var authorProfiles = batchAuthorProfiles(posts);
        List<UUID> postIds = posts.stream().map(Post::getId).toList();
        List<UUID> authorIds = posts.stream().map(p -> p.getAuthorUser().getId()).toList();
        var commentCounts = mapper.batchCommentCounts(postIds);
        var reactionCounts = mapper.batchReactionCounts(postIds);
        var myReactedIds = mapper.batchMyReactedIds(postIds, viewingUserId);
        var authorJoinCounts = mapper.batchAuthorJoinCounts(authorIds);
        var tagsByPostId = mapper.batchTags(postIds);
        var mediaUrlsByPostId = mapper.batchMediaUrls(postIds);
        return posts.stream()
                .map(p -> mapper.toResponse(p, viewingUserId, myJoinStatus(p, viewingUserId), roomIdFor(p),
                        authorProfiles, commentCounts, reactionCounts, myReactedIds, authorJoinCounts,
                        tagsByPostId, mediaUrlsByPostId))
                .toList();
    }

    // ARENA-V2-PRODUCT-ARCHITECTURE.md §3.2 nearby discovery. Same geohash-prefix-then-Haversine
    // approach as FeedRankingService's own "fetch a bounded window, refine in Java" style - see
    // DECISIONS.md for why there's no PostGIS radius query underneath this. Only ACTIVITY/ASK
    // posts with a captured position are candidates; time window filters on startsAt (falls back
    // to createdAt for posts with no explicit start, e.g. an ASK).
    @Transactional(readOnly = true)
    public List<PostResponse> getNearby(UUID viewingUserId, double centerLat, double centerLng, double radiusKm,
                                         Integer withinHours, String intentTypeWire) {
        PostIntentType intentFilter = (intentTypeWire == null || intentTypeWire.isBlank())
                ? null : PostIntentType.valueOf(intentTypeWire.trim().toUpperCase());
        Pageable window = PageRequest.of(0, 500, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Post> candidates = postRepository.findByStatusOrderByCreatedAtDesc(PostStatus.OPEN, window).getContent();

        Instant horizon = withinHours == null ? null : Instant.now().plusSeconds(withinHours * 3600L);
        List<Post> nearby = candidates.stream()
                .filter(Post::isJoinable)
                .filter(p -> p.getApproxLat() != null && p.getApproxLng() != null)
                .filter(p -> intentFilter == null || p.getIntentType() == intentFilter)
                .filter(p -> horizon == null || (p.getStartsAt() != null && p.getStartsAt().isBefore(horizon)))
                .filter(p -> GeohashUtil.distanceKm(centerLat, centerLng, p.getApproxLat(), p.getApproxLng()) <= radiusKm)
                .sorted((a, b) -> Double.compare(
                        GeohashUtil.distanceKm(centerLat, centerLng, a.getApproxLat(), a.getApproxLng()),
                        GeohashUtil.distanceKm(centerLat, centerLng, b.getApproxLat(), b.getApproxLng())))
                .toList();
        nearby = excludeBlocked(nearby, viewingUserId);
        return toResponseList(nearby, viewingUserId);
    }

    @Transactional
    public PostResponse create(UUID userId, CreatePostRequest request) {
        User author = requireUser(userId);
        PostIntentType intentType = PostIntentType.valueOf(request.intentType().trim().toUpperCase());

        // §4 age-gating: ACTIVITY is the real-world-meetup intent type. ASK/UPDATE don't carry
        // the same risk and aren't gated - see DECISIONS.md.
        if (intentType == PostIntentType.ACTIVITY) {
            requireAdult(author);
        }
        cloudinaryService.requireOwnMedia(request.mediaUrls());

        Post.PostBuilder builder = Post.builder()
                .authorUser(author)
                .intentType(intentType)
                .title(request.title())
                .body(request.body())
                .locationText(request.locationText())
                .audience(request.audience() == null ? PostAudience.GLOBAL : PostAudience.valueOf(request.audience().trim().toUpperCase()))
                .visibility(request.visibility() == null ? PostVisibility.PUBLIC : PostVisibility.valueOf(request.visibility().trim().toUpperCase()))
                .capacity(request.capacity())
                .status(PostStatus.OPEN)
                .startsAt(request.startsAt() == null ? null : Instant.parse(request.startsAt()))
                .endsAt(request.endsAt() == null ? null : Instant.parse(request.endsAt()))
                .tags(request.tags())
                .mediaUrls(request.mediaUrls())
                .exactMeetingPoint(request.exactMeetingPoint())
                .requiredVerificationLevel(request.requiredVerificationLevel() == null || request.requiredVerificationLevel().isBlank()
                        ? null : VerificationLevel.valueOf(request.requiredVerificationLevel().trim().toUpperCase()));

        // Geo capture is a per-post, explicit, in-the-moment action (the composer's own
        // Geolocation prompt) - independent of the author's account-wide discovery consent.
        // Same "encode immediately, never persist the raw point" guarantee as
        // CandidateProfileService.updateLocationConsent.
        if (request.lat() != null && request.lng() != null) {
            String geohash = GeohashUtil.encode(request.lat(), request.lng());
            double[] approx = GeohashUtil.decode(geohash);
            builder.geohash(geohash).approxLat(approx[0]).approxLng(approx[1]);
        }

        // §7.3 feed ranking's "relevance" term (Phase C) - computed once here, not on every feed
        // read, so scoring a window of posts is cheap cosine-similarity math, not N embedding
        // calls per request. Tags included since they're often the most topic-dense words on a
        // short post.
        String embeddingInput = request.body() + " " + String.join(" ", request.tags());
        builder.embedding(EmbeddingUtil.encode(embedOrNull(embeddingInput)));

        Post post = postRepository.save(builder.build());
        // §4 safety-audit fix: banned-phrase auto-flag was JobPosting-only before this - every
        // intent type gets scanned, not just ACTIVITY, since an ASK/UPDATE can carry the exact
        // same scam phrasing.
        moderationService.autoFlag(post);
        return mapper.toResponse(post, userId, null, null);
    }

    // ARENA-V2-PRODUCT-ARCHITECTURE.md §3.5/§6 "Company posts appear in the feed" - a company
    // page's own news/hiring/culture post, callable by RECRUITER/COMPANY_ADMIN only (see
    // CompanyPostController). Deliberately GLOBAL/PUBLIC/OPEN and never joinable - same simple
    // shape as an UPDATE post, just attributed to the tenant instead of the acting person.
    @Transactional
    public PostResponse createCompanyPost(UUID userId, CreateCompanyPostRequest request) {
        User author = requireUser(userId);
        EnterpriseProfile company = enterpriseProfileService.getEntityForUser(userId);

        Post post = Post.builder()
                .authorUser(author)
                .authorCompany(company)
                .intentType(PostIntentType.COMPANY)
                .body(request.body())
                .audience(PostAudience.GLOBAL)
                .visibility(PostVisibility.PUBLIC)
                .status(PostStatus.OPEN)
                .tags(request.tags())
                .build();
        post.setEmbedding(EmbeddingUtil.encode(embedOrNull(request.body() + " " + String.join(" ", request.tags()))));
        post = postRepository.save(post);
        moderationService.autoFlag(post);
        return mapper.toResponse(post, userId, null, null);
    }

    // Company page's own post history (mirrors getMyPosts for a candidate) - RECRUITER/
    // COMPANY_ADMIN manage their tenant's posts from here, same pageable shape as everything
    // else in this service.
    @Transactional(readOnly = true)
    public PagedResponse<PostResponse> getCompanyPosts(UUID userId, Pageable pageable) {
        EnterpriseProfile company = enterpriseProfileService.getEntityForUser(userId);
        var page = postRepository.findByAuthorCompanyIdOrderByCreatedAtDesc(company.getId(), pageable);
        return PagedResponse.of(page, p -> mapper.toResponse(p, userId, null, null));
    }

    @Transactional
    public void deleteCompanyPost(UUID userId, UUID postId) {
        EnterpriseProfile company = enterpriseProfileService.getEntityForUser(userId);
        Post post = requirePost(postId);
        if (post.getAuthorCompany() == null || !post.getAuthorCompany().getId().equals(company.getId())) {
            throw new AccessDeniedException("Not your company's post");
        }
        post.setStatus(PostStatus.CANCELLED);
        postRepository.save(post);
    }

    @Transactional
    public PostResponse cancel(UUID userId, UUID postId) {
        Post post = requirePost(postId);
        if (!post.getAuthorUser().getId().equals(userId)) {
            throw new AccessDeniedException("Not your post");
        }
        if (post.getStatus() == PostStatus.CANCELLED || post.getStatus() == PostStatus.CLOSED) {
            throw new BadRequestException("This post is already " + post.getStatus().wireValue());
        }
        post.setStatus(PostStatus.CANCELLED);
        postRepository.save(post);
        roomService.notifyRoomOfCancellation(post);
        return mapper.toResponse(post, userId, null, roomIdFor(post));
    }

    // ARENA-FIX-EVERYTHING.md Phase 1 finding - there was no way for an author to remove their
    // own post at all, anywhere in the product (only cancel(), which keeps it visible with a
    // CANCELLED badge). Deliberately narrower than cancel(): refuses (BadRequestException, not a
    // silent no-op) on anything with real history worth preserving - a post already under
    // moderation review, or a room with actual messages in it - and points the caller at cancel()
    // instead in both cases. Notification rows already sent about this post (join requests, etc.)
    // are a point-in-time record, same as any audit trail - they are not retroactively rewritten
    // by a later delete, matching how every other history/audit record in this codebase behaves.
    @Transactional
    public void delete(UUID userId, UUID postId) {
        Post post = requirePost(postId);
        if (!post.getAuthorUser().getId().equals(userId)) {
            throw new AccessDeniedException("Not your post");
        }
        if (moderationItemRepository.existsByPostId(postId)) {
            throw new BadRequestException("This post is under moderation review and can't be deleted directly - use cancel instead.");
        }
        if (!roomService.deleteRoomForPostIfEmpty(postId)) {
            throw new BadRequestException("This activity has an active room with messages - cancel it instead of deleting.");
        }
        postReactionRepository.deleteByPostId(postId);
        postCommentRepository.deleteByPostId(postId);
        postSaveRepository.deleteByPostId(postId);
        postJoinRequestRepository.deleteByPostId(postId);
        postRepository.delete(post);
    }

    @Transactional
    public PostJoinRequestResponse requestJoin(UUID userId, UUID postId) {
        Post post = requirePost(postId);
        if (!post.isJoinable()) {
            throw new BadRequestException("This post doesn't accept join requests");
        }
        if (post.getAuthorUser().getId().equals(userId)) {
            throw new BadRequestException("You can't join your own post");
        }
        if (postJoinRequestRepository.findByPostIdAndUserId(postId, userId).isPresent()) {
            throw new BadRequestException("You've already requested to join this post");
        }
        if (post.getStatus() != PostStatus.OPEN) {
            throw new BadRequestException("This post is no longer open");
        }
        if (blockService.isBlockedEitherDirection(userId, post.getAuthorUser().getId())) {
            throw new BadRequestException("You can't join this post");
        }

        User user = requireUser(userId);
        if (post.getIntentType() == PostIntentType.ACTIVITY) {
            requireAdult(user);
        }
        if (post.getRequiredVerificationLevel() != null && !user.getVerificationLevel().atLeast(post.getRequiredVerificationLevel())) {
            throw new BadRequestException("This post requires " + post.getRequiredVerificationLevel().wireValue() + " verification to join - check Settings");
        }

        boolean autoApprove = post.getVisibility() == PostVisibility.PUBLIC;
        PostJoinRequest joinRequest = postJoinRequestRepository.save(PostJoinRequest.builder()
                .post(post).user(user)
                .status(autoApprove ? PostJoinStatus.APPROVED : PostJoinStatus.PENDING)
                .decidedAt(autoApprove ? Instant.now() : null)
                .build());

        if (autoApprove) {
            onJoinApproved(post, joinRequest);
        } else {
            notificationService.notifyPostJoinRequested(post, joinRequest);
        }
        return mapper.toResponse(joinRequest);
    }

    @Transactional
    public PostJoinRequestResponse decideJoin(UUID userId, UUID postId, UUID joinRequestId, boolean approve) {
        Post post = requirePost(postId);
        if (!post.getAuthorUser().getId().equals(userId)) {
            throw new AccessDeniedException("Not your post");
        }
        PostJoinRequest joinRequest = postJoinRequestRepository.findById(joinRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Join request not found: " + joinRequestId));
        if (joinRequest.getStatus() != PostJoinStatus.PENDING) {
            throw new BadRequestException("This join request has already been decided");
        }

        joinRequest.setStatus(approve ? PostJoinStatus.APPROVED : PostJoinStatus.DECLINED);
        joinRequest.setDecidedAt(Instant.now());
        postJoinRequestRepository.save(joinRequest);

        if (approve) {
            onJoinApproved(post, joinRequest);
        } else {
            notificationService.notifyPostJoinDeclined(joinRequest);
        }
        return mapper.toResponse(joinRequest);
    }

    @Transactional(readOnly = true)
    public List<PostJoinRequestResponse> getJoinRequests(UUID userId, UUID postId) {
        Post post = requirePost(postId);
        if (!post.getAuthorUser().getId().equals(userId)) {
            throw new AccessDeniedException("Not your post");
        }
        return mapper.toResponseList(postJoinRequestRepository.findByPostIdOrderByCreatedAtAsc(postId));
    }

    // Shared by both the PUBLIC-auto-approve path and the APPROVAL-manual-approve path so the
    // room/spots/notification side effects only ever live in one place.
    private void onJoinApproved(Post post, PostJoinRequest joinRequest) {
        Room room = roomService.getOrCreateForPost(post);
        roomService.addMember(room, joinRequest.getUser());

        post.setSpotsFilled(post.getSpotsFilled() + 1);
        if (post.getCapacity() != null && post.getSpotsFilled() >= post.getCapacity()) {
            post.setStatus(PostStatus.FULL);
        }
        postRepository.save(post);

        notificationService.notifyPostJoinApproved(joinRequest);
    }

    private void requireAdult(User user) {
        if (user.getDateOfBirth() == null) {
            throw new BadRequestException("Add your date of birth in Settings before creating or joining an activity");
        }
        if (!AgeUtil.isAdult(user.getDateOfBirth())) {
            throw new BadRequestException("You must be " + AgeUtil.MINIMUM_AGE + " or older to create or join an activity");
        }
    }

    private List<Post> excludeBlocked(List<Post> posts, UUID viewingUserId) {
        if (viewingUserId == null || posts.isEmpty()) return posts;
        return posts.stream().filter(p -> !blockService.isBlockedEitherDirection(viewingUserId, p.getAuthorUser().getId())).toList();
    }

    private String myJoinStatus(Post post, UUID viewingUserId) {
        if (viewingUserId == null || !post.isJoinable()) return null;
        return postJoinRequestRepository.findByPostIdAndUserId(post.getId(), viewingUserId)
                .map(j -> j.getStatus().wireValue()).orElse(null);
    }

    private String roomIdFor(Post post) {
        if (!post.isJoinable()) return null;
        return roomService.findRoomIdForPost(post.getId()).orElse(null);
    }

    // PART 6 "SAVE POST|DELETE /posts/{id}/save GET /posts/saved" (PostCard overflow menu's
    // Save action). Idempotent both ways - saving an already-saved post or unsaving a
    // never-saved one is a no-op, not an error, matching FollowService's own idempotent style.
    @Transactional
    public void save(UUID userId, UUID postId) {
        if (postSaveRepository.existsByPostIdAndUserId(postId, userId)) return;
        Post post = requirePost(postId);
        User user = requireUser(userId);
        postSaveRepository.save(PostSave.builder().post(post).user(user).build());
    }

    @Transactional
    public void unsave(UUID userId, UUID postId) {
        postSaveRepository.findByPostIdAndUserId(postId, userId).ifPresent(postSaveRepository::delete);
    }

    @Transactional(readOnly = true)
    public PagedResponse<PostResponse> getSaved(UUID userId, Pageable pageable) {
        var page = postSaveRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        List<Post> posts = page.getContent().stream().map(PostSave::getPost).toList();
        return new PagedResponse<>(toResponseList(posts, userId), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    private Map<UUID, CandidateProfile> batchAuthorProfiles(List<Post> posts) {
        if (posts.isEmpty()) return Map.of();
        List<UUID> authorIds = posts.stream().map(p -> p.getAuthorUser().getId()).distinct().toList();
        return candidateProfileRepository.findByUserIdIn(authorIds).stream()
                .collect(Collectors.toMap(c -> c.getUser().getId(), c -> c));
    }

    private Post requirePost(UUID id) {
        return postRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Post not found: " + id));
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    // P3 audit fix: embeddingProvider.embed() used to propagate straight into post creation -
    // a transient OpenAI outage (the only provider that can actually throw here; the active
    // default HashingEmbeddingProvider is pure local math and never fails) would surface as a
    // raw upstream error message wrapped in a 400, via GlobalExceptionHandler's generic
    // RuntimeException handler. Every other external dependency in this codebase (email, Teams,
    // WhatsApp) is best-effort and never blocks the primary operation - this makes embedding
    // follow the same contract. A post with a null embedding just falls out of similarity-based
    // ranking until the next successful embed, which is a much smaller failure than blocking
    // post creation entirely.
    private float[] embedOrNull(String text) {
        try {
            return embeddingProvider.embed(text);
        } catch (Exception e) {
            log.warn("Embedding failed, post will save without one: {}", e.getMessage());
            return null;
        }
    }
}
