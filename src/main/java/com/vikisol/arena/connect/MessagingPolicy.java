package com.vikisol.arena.connect;

import com.vikisol.arena.applications.repository.ApplicationRepository;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.service.EnterpriseProfileService;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

// Flow §8 Messages: someone on a company team may start a chat with a person only after that
// person applied to the company or accepted its connect request (FE-API-GAPS row 34). Chats that
// already exist carry on.
@Component
@RequiredArgsConstructor
public class MessagingPolicy {

    static final Set<Role> EMPLOYER_ROLES = Set.of(Role.RECRUITER, Role.COMPANY_ADMIN, Role.HIRING_MANAGER);

    private final ConnectRequestRepository connectRequestRepository;
    private final EnterpriseProfileService enterpriseProfileService;
    private final CandidateProfileRepository candidateProfileRepository;
    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public boolean mayStartChat(User sender, UUID recipientUserId) {
        if (!EMPLOYER_ROLES.contains(sender.getRole())) return true;
        User recipient = userRepository.findById(recipientUserId).orElse(null);
        // ARCHITECT-REVIEW-BE-1 blocker #3: a missing recipient and a sender with no tenant both
        // used to fall through to "allowed" (fail-open), not "unverified, so blocked". An
        // employer-role account with a broken/missing enterprise link could message any talent.
        if (recipient == null) return false;
        if (recipient.getRole() != Role.TALENT) return true;
        EnterpriseProfile tenant = enterpriseProfileService.findEntityForUser(sender.getId()).orElse(null);
        if (tenant == null) return false;
        if (connectRequestRepository.existsByTenantIdAndCandidateIdAndStatus(tenant.getId(), recipientUserId, ConnectRequest.Status.ACCEPTED)) {
            return true;
        }
        return candidateProfileRepository.findByUserId(recipientUserId)
                .map(c -> applicationRepository.existsByCandidateIdAndJobPostingEnterpriseId(c.getId(), tenant.getId()))
                .orElse(false);
    }
}
