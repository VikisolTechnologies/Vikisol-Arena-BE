package com.vikisol.arena.follows.service;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import com.vikisol.arena.follows.dto.FollowCountsResponse;
import com.vikisol.arena.follows.dto.FollowerResponse;
import com.vikisol.arena.follows.entity.Follow;
import com.vikisol.arena.follows.entity.FollowTargetType;
import com.vikisol.arena.follows.repository.FollowRepository;
import com.vikisol.arena.notifications.service.NotificationService;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final EnterpriseProfileRepository enterpriseProfileRepository;
    private final NotificationService notificationService;

    @Transactional
    public void follow(UUID followerUserId, UUID followingUserId) {
        if (followerUserId.equals(followingUserId)) {
            throw new BadRequestException("You can't follow yourself");
        }
        if (followRepository.existsByFollowerUserIdAndFollowingUserId(followerUserId, followingUserId)) {
            return; // idempotent
        }
        User follower = requireUser(followerUserId);
        User following = requireUser(followingUserId);
        followRepository.save(Follow.builder().followerUser(follower).followingUser(following).build());
        notificationService.notifyNewFollower(following, follower);
    }

    @Transactional
    public void unfollow(UUID followerUserId, UUID followingUserId) {
        followRepository.findByFollowerUserIdAndFollowingUserId(followerUserId, followingUserId)
                .ifPresent(followRepository::delete);
    }

    @Transactional(readOnly = true)
    public FollowCountsResponse getCounts(UUID userId, UUID viewingUserId) {
        Boolean viewerFollows = viewingUserId == null ? null
                : followRepository.existsByFollowerUserIdAndFollowingUserId(viewingUserId, userId);
        return new FollowCountsResponse(userId.toString(),
                followRepository.countByFollowingUserId(userId),
                followRepository.countByFollowerUserId(userId),
                viewerFollows);
    }

    @Transactional(readOnly = true)
    public List<FollowerResponse> getFollowers(UUID userId) {
        return followRepository.findByFollowingUserIdOrderByCreatedAtDesc(userId).stream()
                .map(f -> toResponse(f.getFollowerUser(), f.getCreatedAt().toString())).toList();
    }

    @Transactional(readOnly = true)
    public List<FollowerResponse> getFollowing(UUID userId) {
        return followRepository.findByFollowerUserIdOrderByCreatedAtDesc(userId).stream()
                .map(f -> toResponse(f.getFollowingUser(), f.getCreatedAt().toString())).toList();
    }

    private FollowerResponse toResponse(User user, String followedAt) {
        String name = user.getName();
        String emoji = "🧑🏽";
        var profile = candidateProfileRepository.findByUserId(user.getId());
        if (profile.isPresent()) {
            name = profile.get().getName();
            emoji = profile.get().getAvatarEmoji();
        }
        return new FollowerResponse(user.getId().toString(), name, emoji, followedAt);
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    // ARENA-V2-PRODUCT-ARCHITECTURE.md Phase C company pages - same idempotent shape as
    // follow(User)/unfollow(User) above, just against EnterpriseProfile instead of User.
    @Transactional
    public void followCompany(UUID followerUserId, UUID companyId) {
        if (followRepository.existsByFollowerUserIdAndFollowingCompanyId(followerUserId, companyId)) {
            return; // idempotent
        }
        User follower = requireUser(followerUserId);
        EnterpriseProfile company = requireCompany(companyId);
        followRepository.save(Follow.builder().followerUser(follower).followingCompany(company)
                .targetType(FollowTargetType.COMPANY).build());
    }

    @Transactional
    public void unfollowCompany(UUID followerUserId, UUID companyId) {
        followRepository.findByFollowerUserIdAndFollowingCompanyId(followerUserId, companyId)
                .ifPresent(followRepository::delete);
    }

    @Transactional(readOnly = true)
    public long getCompanyFollowerCount(UUID companyId) {
        return followRepository.countByFollowingCompanyId(companyId);
    }

    @Transactional(readOnly = true)
    public boolean viewerFollowsCompany(UUID viewingUserId, UUID companyId) {
        return viewingUserId != null && followRepository.existsByFollowerUserIdAndFollowingCompanyId(viewingUserId, companyId);
    }

    private EnterpriseProfile requireCompany(UUID id) {
        return enterpriseProfileRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Company not found: " + id));
    }
}
