package com.vikisol.arena.posts.service;

import com.vikisol.arena.common.geo.GeohashUtil;
import com.vikisol.arena.posts.dto.PostJoinRequestResponse;
import com.vikisol.arena.posts.dto.PostResponse;
import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.entity.PostJoinRequest;
import com.vikisol.arena.posts.repository.PostCommentRepository;
import com.vikisol.arena.posts.repository.PostJoinRequestRepository;
import com.vikisol.arena.posts.repository.PostReactionRepository;
import com.vikisol.arena.posts.repository.PostRepository;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PostMapper {

    private final CandidateProfileRepository candidateProfileRepository;
    private final PostCommentRepository postCommentRepository;
    private final PostReactionRepository postReactionRepository;
    private final PostJoinRequestRepository postJoinRequestRepository;
    private final PostRepository postRepository;

    // Single-post convenience overload (a few extra queries) - list/feed call sites should use
    // the batched overload below instead, same split as ProjectMapper's toResponse(Bid) vs
    // toResponse(Bid, Map).
    public PostResponse toResponse(Post post, UUID viewingUserId, String myJoinStatus, String roomId) {
        var profile = candidateProfileRepository.findByUserId(post.getAuthorUser().getId());
        long commentCount = postCommentRepository.countByPostId(post.getId());
        long reactionCount = postReactionRepository.countByPostId(post.getId());
        Boolean myReacted = viewingUserId == null ? null : postReactionRepository.existsByPostIdAndUserId(post.getId(), viewingUserId);
        long authorJoinCount = postJoinRequestRepository.countApprovedByUserIdIn(List.of(post.getAuthorUser().getId())).stream()
                .mapToLong(PostJoinRequestRepository.UserJoinCountProjection::getCnt).sum();
        return toResponse(post, viewingUserId, myJoinStatus, roomId, profile.orElse(null), commentCount, reactionCount, myReacted, authorJoinCount,
                post.getTags(), post.getMediaUrls());
    }

    public PostResponse toResponse(Post post, UUID viewingUserId, String myJoinStatus, String roomId,
                                    Map<UUID, CandidateProfile> authorProfiles) {
        return toResponse(post, viewingUserId, myJoinStatus, roomId, authorProfiles, Map.of(), Map.of(), Set.of(), Map.of(), Map.of(), Map.of());
    }

    // Fully batched overload - one count query and one reaction-membership query for the entire
    // page/window, instead of two extra queries per post. Feed/trending/nearby call sites use
    // this; batchCommentCounts/batchReactionCounts/batchAuthorJoinCounts/batchTags/
    // batchMediaUrls build the maps once per list.
    public PostResponse toResponse(Post post, UUID viewingUserId, String myJoinStatus, String roomId,
                                    Map<UUID, CandidateProfile> authorProfiles,
                                    Map<UUID, Long> commentCounts, Map<UUID, Long> reactionCounts, Set<UUID> myReactedIds,
                                    Map<UUID, Long> authorJoinCounts,
                                    Map<UUID, List<String>> tagsByPostId, Map<UUID, List<String>> mediaUrlsByPostId) {
        UUID authorId = post.getAuthorUser().getId();
        return toResponse(post, viewingUserId, myJoinStatus, roomId, authorProfiles.get(authorId),
                commentCounts.getOrDefault(post.getId(), 0L), reactionCounts.getOrDefault(post.getId(), 0L),
                viewingUserId == null ? null : myReactedIds.contains(post.getId()),
                authorJoinCounts.getOrDefault(authorId, 0L),
                tagsByPostId.getOrDefault(post.getId(), List.of()), mediaUrlsByPostId.getOrDefault(post.getId(), List.of()));
    }

    public Map<UUID, Long> batchCommentCounts(List<UUID> postIds) {
        if (postIds.isEmpty()) return Map.of();
        return postCommentRepository.countByPostIdIn(postIds).stream()
                .collect(Collectors.toMap(PostCommentRepository.PostCountProjection::getPostId, PostCommentRepository.PostCountProjection::getCnt));
    }

    public Map<UUID, Long> batchReactionCounts(List<UUID> postIds) {
        if (postIds.isEmpty()) return Map.of();
        return postReactionRepository.countByPostIdIn(postIds).stream()
                .collect(Collectors.toMap(PostCommentRepository.PostCountProjection::getPostId, PostCommentRepository.PostCountProjection::getCnt));
    }

    public Set<UUID> batchMyReactedIds(List<UUID> postIds, UUID viewingUserId) {
        if (postIds.isEmpty() || viewingUserId == null) return Set.of();
        return postReactionRepository.findReactedPostIdsByUserIdAndPostIdIn(viewingUserId, postIds);
    }

    // §4 safety-audit addition - "Show join-count ... and account age" batched by author id for
    // a whole feed/nearby window, same shape as the comment/reaction batch counts above.
    public Map<UUID, Long> batchAuthorJoinCounts(List<UUID> authorUserIds) {
        if (authorUserIds.isEmpty()) return Map.of();
        return postJoinRequestRepository.countApprovedByUserIdIn(authorUserIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(PostJoinRequestRepository.UserJoinCountProjection::getUserId,
                        PostJoinRequestRepository.UserJoinCountProjection::getCnt));
    }

    // P3 audit fix: toResponse used to read post.getTags()/getMediaUrls() directly - one lazy
    // load per post per page, for each of the two collections. IN-queries instead, grouped by
    // post id once per page/window - same shape as the count batches above.
    public Map<UUID, List<String>> batchTags(List<UUID> postIds) {
        if (postIds.isEmpty()) return Map.of();
        return postRepository.findTagsByPostIdIn(postIds).stream()
                .collect(Collectors.groupingBy(PostRepository.PostElementProjection::getPostId,
                        Collectors.mapping(PostRepository.PostElementProjection::getValue, Collectors.toList())));
    }

    public Map<UUID, List<String>> batchMediaUrls(List<UUID> postIds) {
        if (postIds.isEmpty()) return Map.of();
        return postRepository.findMediaUrlsByPostIdIn(postIds).stream()
                .collect(Collectors.groupingBy(PostRepository.PostElementProjection::getPostId,
                        Collectors.mapping(PostRepository.PostElementProjection::getValue, Collectors.toList())));
    }

    private PostResponse toResponse(Post post, UUID viewingUserId, String myJoinStatus, String roomId, CandidateProfile authorProfile,
                                     long commentCount, long reactionCount, Boolean myReacted, long authorJoinCount,
                                     List<String> tags, List<String> mediaUrls) {
        String authorName = post.getAuthorUser().getName();
        String authorEmoji = "🧑🏽";
        if (authorProfile != null) {
            authorName = authorProfile.getName();
            authorEmoji = authorProfile.getAvatarEmoji();
        }
        // A COMPANY post displays as the company, not the recruiter/company_admin who actually
        // clicked publish - authorUser stays the acting account for permission checks (see
        // Post.authorUser's own comment), this only overrides what's shown.
        String authorCompanyId = null;
        if (post.getAuthorCompany() != null) {
            authorCompanyId = post.getAuthorCompany().getId().toString();
            authorName = post.getAuthorCompany().getCompanyName();
            authorEmoji = post.getAuthorCompany().getLogoEmoji();
        }
        boolean mine = viewingUserId != null && post.getAuthorUser().getId().equals(viewingUserId);

        // §4: "exact meeting point revealed only inside the room, only to approved joiners" -
        // mine-or-approved is exactly that gate, using information already resolved by the
        // caller (PostService) rather than this mapper needing its own room-membership query.
        boolean canSeeExactMeetingPoint = mine || "approved".equals(myJoinStatus);

        // A second, independent jitter on top of the already-coarse stored approxLat/approxLng -
        // see GeohashUtil.jitter's own doc comment and DECISIONS.md. Only computed when a
        // position actually exists; never derived from anything more precise than what's stored.
        Double displayLat = null, displayLng = null;
        if (post.getApproxLat() != null && post.getApproxLng() != null) {
            double[] jittered = GeohashUtil.jitter(post.getApproxLat(), post.getApproxLng(), 150);
            displayLat = jittered[0];
            displayLng = jittered[1];
        }

        long authorAccountAgeDays = Duration.between(post.getAuthorUser().getCreatedAt(), Instant.now()).toDays();

        return new PostResponse(
                post.getId().toString(), post.getAuthorUser().getId().toString(), authorName, authorEmoji, authorCompanyId,
                post.getIntentType().wireValue(), post.getTitle(), post.getBody(), post.getLocationText(),
                post.getAudience().wireValue(), post.getVisibility().wireValue(),
                post.getCapacity(), post.getSpotsFilled(), post.getStatus().wireValue(),
                post.getStartsAt() == null ? null : post.getStartsAt().toString(),
                post.getEndsAt() == null ? null : post.getEndsAt().toString(),
                tags, mediaUrls, post.isJoinable(),
                mine ? Boolean.TRUE : null, myJoinStatus, roomId, post.getCreatedAt().toString(),
                displayLat, displayLng,
                canSeeExactMeetingPoint ? post.getExactMeetingPoint() : null,
                post.getRequiredVerificationLevel() == null ? null : post.getRequiredVerificationLevel().wireValue(),
                commentCount, reactionCount, myReacted,
                authorJoinCount, Math.max(0, authorAccountAgeDays),
                post.isDemoContent()
        );
    }

    public PostJoinRequestResponse toResponse(PostJoinRequest joinRequest) {
        var profile = candidateProfileRepository.findByUserId(joinRequest.getUser().getId());
        String userName = joinRequest.getUser().getName();
        String userEmoji = "🧑🏽";
        if (profile.isPresent()) {
            userName = profile.get().getName();
            userEmoji = profile.get().getAvatarEmoji();
        }
        return new PostJoinRequestResponse(
                joinRequest.getId().toString(), joinRequest.getPost().getId().toString(),
                joinRequest.getUser().getId().toString(), userName, userEmoji,
                joinRequest.getStatus().wireValue(), joinRequest.getCreatedAt().toString());
    }

    public List<PostJoinRequestResponse> toResponseList(List<PostJoinRequest> requests) {
        return requests.stream().map(this::toResponse).toList();
    }
}
