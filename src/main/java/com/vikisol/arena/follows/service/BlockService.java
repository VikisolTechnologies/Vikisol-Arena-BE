package com.vikisol.arena.follows.service;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.follows.dto.BlockedUserResponse;
import com.vikisol.arena.follows.entity.UserBlock;
import com.vikisol.arena.follows.repository.FollowRepository;
import com.vikisol.arena.follows.repository.UserBlockRepository;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BlockService {

    private final UserBlockRepository userBlockRepository;
    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;

    @Transactional
    public void block(UUID blockerUserId, UUID blockedUserId) {
        if (blockerUserId.equals(blockedUserId)) {
            throw new BadRequestException("You can't block yourself");
        }
        if (!userBlockRepository.existsByBlockerUserIdAndBlockedUserId(blockerUserId, blockedUserId)) {
            User blocker = requireUser(blockerUserId);
            User blocked = requireUser(blockedUserId);
            userBlockRepository.save(UserBlock.builder().blockerUser(blocker).blockedUser(blocked).build());
        }
        // Blocking someone implicitly un-follows in both directions - staying "following" a
        // person you just blocked (or who blocked you) doesn't make sense.
        followRepository.findByFollowerUserIdAndFollowingUserId(blockerUserId, blockedUserId).ifPresent(followRepository::delete);
        followRepository.findByFollowerUserIdAndFollowingUserId(blockedUserId, blockerUserId).ifPresent(followRepository::delete);
    }

    @Transactional
    public void unblock(UUID blockerUserId, UUID blockedUserId) {
        userBlockRepository.deleteByBlockerUserIdAndBlockedUserId(blockerUserId, blockedUserId);
    }

    /** Either direction blocks interaction - being blocked BY someone stops you from reaching
     * them too, not just the reverse. Used by PostService (join gating) and RoomService. */
    @Transactional(readOnly = true)
    public boolean isBlockedEitherDirection(UUID userA, UUID userB) {
        return userBlockRepository.existsByBlockerUserIdAndBlockedUserId(userA, userB)
                || userBlockRepository.existsByBlockerUserIdAndBlockedUserId(userB, userA);
    }

    @Transactional(readOnly = true)
    public List<UUID> getBlockedUserIds(UUID blockerUserId) {
        return userBlockRepository.findByBlockerUserId(blockerUserId).stream()
                .map(b -> b.getBlockedUser().getId()).toList();
    }

    @Transactional(readOnly = true)
    public List<BlockedUserResponse> getMyBlocks(UUID blockerUserId) {
        return userBlockRepository.findByBlockerUserIdOrderByCreatedAtDesc(blockerUserId).stream()
                .map(b -> {
                    User blocked = b.getBlockedUser();
                    String name = blocked.getName();
                    String emoji = "🧑🏽";
                    var profile = candidateProfileRepository.findByUserId(blocked.getId());
                    if (profile.isPresent()) {
                        name = profile.get().getName();
                        emoji = profile.get().getAvatarEmoji();
                    }
                    return new BlockedUserResponse(blocked.getId().toString(), name, emoji, b.getCreatedAt().toString());
                }).toList();
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }
}
