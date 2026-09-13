package com.vikisol.arena.platform.service;

import com.vikisol.arena.audit.AuditActions;
import com.vikisol.arena.audit.AuditService;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.enterprise.repository.MembershipRepository;
import com.vikisol.arena.platform.dto.PlatformUserResponse;
import com.vikisol.arena.profile.service.CandidateProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// PA3 (global user search, across talent and every enterprise role).
@Service
@RequiredArgsConstructor
public class PlatformUserService {

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final CandidateProfileService candidateProfileService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PagedResponse<PlatformUserResponse> search(String query, String roleWire, Pageable pageable) {
        String q = query == null ? "" : query.trim();
        Role role = (roleWire == null || roleWire.isBlank()) ? null : Role.fromWireValue(roleWire);
        return PagedResponse.of(userRepository.search(q, role, pageable), this::toResponse);
    }

    // ARENA-FIX-EVERYTHING.md Phase 1 - found while cleaning up fake QA candidate accounts
    // polluting enterprise talent search (10 of 47 results). Arena had no admin path to remove a
    // user at all; building a genuine cascading hard-delete across 30+ tables (several with no
    // repository support for a user-scoped delete/find at all) was assessed as too large and
    // risky for this pass, and would have duplicated a decision this codebase already made
    // deliberately - CandidateProfileService's erasure path (the DPDP right-to-erasure logic
    // behind DELETE /profile/me) already anonymizes + permanently disables login rather than
    // hard-deleting, for exactly that reason. This reuses that exact same, already-audited logic
    // via its eraseAccountAsAdmin entry point, just admin-triggered against a target user instead
    // of self-triggered - no new deletion mechanism, no new risk surface.
    // Deliberately idempotent rather than refusing an already-erased account: eraseAccountAsAdmin
    // only ever re-applies the same anonymization (name/bio/skills/etc. to their already-erased
    // values, tokens re-revoked harmlessly) - safe to repeat, and needs to stay that way so a
    // later anonymization fix (e.g. a field eraseAccountAsAdmin didn't yet clear) can be re-run
    // against an already-erased account instead of being permanently stuck on whatever it wrote
    // the first time.
    @Transactional
    public void eraseAccount(UUID actorUserId, UUID targetUserId) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetUserId));
        if (target.getRole() != Role.TALENT) {
            throw new BadRequestException("Only talent accounts can be erased this way - " + target.getRole().wireValue() + " accounts aren't supported.");
        }
        String targetDescription = target.getName() + " (" + target.getEmail() + ")";
        candidateProfileService.eraseAccountAsAdmin(targetUserId);
        auditService.record(null, actorUserId, AuditActions.ACCOUNT_ERASED_BY_ADMIN, targetDescription);
    }

    private PlatformUserResponse toResponse(User u) {
        String tenantId = null;
        String tenantName = null;
        if (u.getRole().hasTenant()) {
            var membership = membershipRepository.findByUserId(u.getId());
            if (membership.isPresent()) {
                tenantId = membership.get().getTenant().getId().toString();
                tenantName = membership.get().getTenant().getCompanyName();
            }
        }
        return new PlatformUserResponse(u.getId().toString(), u.getName(), u.getEmail(), u.getRole().wireValue(),
                tenantId, tenantName, u.getCreatedAt().toString());
    }
}
