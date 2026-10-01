package com.vikisol.arena.profile.service;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.follows.service.BlockService;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * ARCHITECT-REVIEW-BE-1 blocker #2: {@code GET /profile/{id}} was the only endpoint that checked
 * profile visibility / block / ban / deletion before showing a person's data. {@code
 * CandidateProfileService.getPublicProfile} had its own private copy of this check; every other
 * per-person endpoint ({@code /needs/outcomes/{userId}}, {@code /projects/of/{userId}}, {@code
 * /profile/{userId}/stats}, {@code /posts/by-user/{userId}}, {@code POST /profile/{id}/report},
 * {@code ConnectService.send}) skipped it entirely, so a hidden/blocked/banned/deleted person's
 * activity, stats and projects were readable, and they could still be reported or connect-requested
 * by someone who should never have been able to find them.
 *
 * <p>One shared check now, called at the start of every one of those endpoints - same 404 either
 * way (missing or just not visible) so the response never confirms the person exists.
 */
@Component
@RequiredArgsConstructor
public class ProfileVisibilityGuard {

    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final BlockService blockService;

    /**
     * Throws a 404 unless {@code viewerId} is {@code targetUserId} themself, or the target is a
     * live, non-banned, non-blocked-either-direction account whose profile visibility allows this
     * viewer. A target user with no {@link CandidateProfile} at all (e.g. a company account) is
     * always visible - this guard is about talent profiles, not every user row.
     */
    @Transactional(readOnly = true)
    public void requireVisibleTo(UUID viewerId, UUID targetUserId) {
        if (targetUserId == null) throw new ResourceNotFoundException("Person not found");
        if (targetUserId.equals(viewerId)) return;
        User target = userRepository.findById(targetUserId).orElseThrow(() -> new ResourceNotFoundException("Person not found"));
        if (target.getDeletedAt() != null || target.getBannedAt() != null) throw new ResourceNotFoundException("Person not found");
        if (viewerId != null && blockService.isBlockedEitherDirection(viewerId, targetUserId)) {
            throw new ResourceNotFoundException("Person not found");
        }
        CandidateProfile profile = candidateProfileRepository.findByUserId(targetUserId).orElse(null);
        if (profile == null) return; // no candidate profile (e.g. a company account) - nothing to hide
        boolean visible = switch (profile.getProfileVisibility()) {
            case EVERYONE -> true;
            case NEARBY -> viewerId != null;
            case HIDDEN -> false;
        };
        if (!visible) throw new ResourceNotFoundException("Person not found");
    }
}
