package com.vikisol.arena.posts.service;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.notifications.service.NotificationService;
import com.vikisol.arena.posts.dto.CreatePostRequest;
import com.vikisol.arena.posts.dto.PostJoinRequestResponse;
import com.vikisol.arena.posts.dto.PostResponse;
import com.vikisol.arena.posts.entity.*;
import com.vikisol.arena.posts.repository.PostJoinRequestRepository;
import com.vikisol.arena.posts.repository.PostRepository;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import com.vikisol.arena.rooms.entity.Room;
import com.vikisol.arena.rooms.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final PostJoinRequestRepository postJoinRequestRepository;
    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final PostMapper mapper;
    private final FeedRankingService feedRankingService;
    private final RoomService roomService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<PostResponse> getFeed(UUID viewingUserId, int page, int size) {
        List<Post> window = feedRankingService.getFeedWindow(viewingUserId, page, size);
        var authorProfiles = batchAuthorProfiles(window);
        return window.stream()
                .map(p -> mapper.toResponse(p, viewingUserId, myJoinStatus(p, viewingUserId), roomIdFor(p), authorProfiles))
                .toList();
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

    @Transactional
    public PostResponse create(UUID userId, CreatePostRequest request) {
        User author = requireUser(userId);
        Post post = Post.builder()
                .authorUser(author)
                .intentType(PostIntentType.valueOf(request.intentType().trim().toUpperCase()))
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
                .build();
        post = postRepository.save(post);
        return mapper.toResponse(post, userId, null, null);
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

        User user = requireUser(userId);
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

    private String myJoinStatus(Post post, UUID viewingUserId) {
        if (viewingUserId == null || !post.isJoinable()) return null;
        return postJoinRequestRepository.findByPostIdAndUserId(post.getId(), viewingUserId)
                .map(j -> j.getStatus().wireValue()).orElse(null);
    }

    private String roomIdFor(Post post) {
        if (!post.isJoinable()) return null;
        return roomService.findRoomIdForPost(post.getId()).orElse(null);
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
}
