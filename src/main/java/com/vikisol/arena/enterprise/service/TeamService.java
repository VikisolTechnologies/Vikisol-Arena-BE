package com.vikisol.arena.enterprise.service;

import com.vikisol.arena.audit.AuditActions;
import com.vikisol.arena.audit.AuditService;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.util.HandleGenerator;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.enterprise.dto.admin.ChangeRoleRequest;
import com.vikisol.arena.enterprise.dto.admin.InvitationPreviewResponse;
import com.vikisol.arena.enterprise.dto.admin.InvitationResponse;
import com.vikisol.arena.enterprise.dto.admin.InviteMemberRequest;
import com.vikisol.arena.enterprise.dto.admin.TeamMemberResponse;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.entity.Invitation;
import com.vikisol.arena.enterprise.entity.InvitationStatus;
import com.vikisol.arena.enterprise.entity.Membership;
import com.vikisol.arena.enterprise.entity.MembershipStatus;
import com.vikisol.arena.enterprise.repository.InvitationRepository;
import com.vikisol.arena.enterprise.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * CA2 (Team management): invite by email, list pending invitations, change roles,
 * suspend/remove, seat-limit enforcement. No email provider is configured (NoopEmailProvider,
 * see integration package) so invite links are surfaced directly in InvitationResponse for the
 * admin UI to show/copy, per ARENA-ENTERPRISE-SUITE.md's "mock email surface" note - the accept
 * flow itself (token -> real account) is fully real regardless of how the link reached them.
 */
@Service
@RequiredArgsConstructor
public class TeamService {

    private static final Set<Role> INVITABLE_ROLES = Set.of(Role.RECRUITER, Role.COMPANY_ADMIN, Role.HIRING_MANAGER);

    private final MembershipRepository membershipRepository;
    private final InvitationRepository invitationRepository;
    private final EnterpriseProfileService enterpriseProfileService;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    // Real bug, found live-testing an unrelated flow (AuthService's forgot-password): this was
    // "${app.frontend.url:...}" (dotted), but application.yml's actual key is the hyphenated
    // "app.frontend-url" - the dotted version never matched, so this silently resolved to the
    // localhost fallback on every deployment, FRONTEND_URL env var or not. Every invite email's
    // link has been pointing at http://localhost:3000/invite/{token} in production the whole
    // time - masked only by NoopEmailProvider being the active provider (nothing has actually
    // been delivered to a real inbox yet to expose it). Would have broken the instant a real
    // email provider got configured.
    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Transactional(readOnly = true)
    public List<TeamMemberResponse> listMembers(UUID adminUserId) {
        EnterpriseProfile tenant = enterpriseProfileService.getEntityForUser(adminUserId);
        return membershipRepository.findByTenantIdOrderByCreatedAtDesc(tenant.getId()).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> listPendingInvitations(UUID adminUserId) {
        EnterpriseProfile tenant = enterpriseProfileService.getEntityForUser(adminUserId);
        return invitationRepository.findByTenantIdAndStatus(tenant.getId(), InvitationStatus.PENDING).stream()
                .map(this::toResponse).toList();
    }

    @Transactional
    public InvitationResponse invite(UUID adminUserId, InviteMemberRequest request) {
        EnterpriseProfile tenant = enterpriseProfileService.getEntityForUser(adminUserId);
        Role role = Role.fromWireValue(request.role());
        if (!INVITABLE_ROLES.contains(role)) {
            throw new BadRequestException("Can't invite someone as " + role.wireValue());
        }
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BadRequestException("An account with this email already exists");
        }

        // Seat limit check (CA2: "seat usage vs plan limit with upsell when full") - active
        // members + pending invitations both count against the seat total, so an admin can't
        // invite past the limit and then have every invite silently exceed it once accepted.
        long activeMembers = membershipRepository.countByTenantIdAndStatus(tenant.getId(), MembershipStatus.ACTIVE);
        long pendingInvites = invitationRepository.findByTenantIdAndStatus(tenant.getId(), InvitationStatus.PENDING).size();
        if (activeMembers + pendingInvites >= tenant.getSeatsTotal()) {
            throw new BadRequestException("Your " + tenant.getPlan().wireValue() + " plan allows "
                    + tenant.getSeatsTotal() + " seats - upgrade your plan to invite more people.");
        }

        Invitation invitation = invitationRepository.save(Invitation.builder()
                .tenant(tenant).email(request.email().toLowerCase()).role(role)
                .token(UUID.randomUUID().toString())
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .invitedBy(userRepository.getReferenceById(adminUserId))
                .status(InvitationStatus.PENDING)
                .build());

        auditService.record(tenant.getId(), adminUserId, AuditActions.MEMBER_INVITED,
                request.email() + " as " + role.wireValue());
        return toResponse(invitation);
    }

    @Transactional
    public void revokeInvitation(UUID adminUserId, UUID invitationId) {
        EnterpriseProfile tenant = enterpriseProfileService.getEntityForUser(adminUserId);
        Invitation invitation = requireInvitation(invitationId);
        requireSameTenant(invitation.getTenant(), tenant);
        invitation.setStatus(InvitationStatus.REVOKED);
        invitationRepository.save(invitation);
    }

    @Transactional(readOnly = true)
    public InvitationPreviewResponse previewInvitation(String token) {
        var invitationOpt = invitationRepository.findByToken(token);
        if (invitationOpt.isEmpty()) {
            return new InvitationPreviewResponse(null, null, null, null, false, "This invite link isn't valid");
        }
        Invitation invitation = invitationOpt.get();
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            return new InvitationPreviewResponse(invitation.getEmail(), invitation.getRole().wireValue(),
                    invitation.getTenant().getCompanyName(), invitation.getTenant().getLogoEmoji(),
                    false, "This invite has already been used or revoked");
        }
        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            return new InvitationPreviewResponse(invitation.getEmail(), invitation.getRole().wireValue(),
                    invitation.getTenant().getCompanyName(), invitation.getTenant().getLogoEmoji(),
                    false, "This invite has expired - ask your admin to send a new one");
        }
        return new InvitationPreviewResponse(invitation.getEmail(), invitation.getRole().wireValue(),
                invitation.getTenant().getCompanyName(), invitation.getTenant().getLogoEmoji(), true, null);
    }

    /** Called from the (unauthenticated) accept-invite endpoint - creates the User + Membership
     * together, matching AuthService.signUp()'s "everything or nothing" transactionality. */
    @Transactional
    public User acceptInvitation(String token, String name, String password) {
        Invitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("This invite link isn't valid"));
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new BadRequestException("This invite has already been used or revoked");
        }
        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("This invite has expired - ask your admin to send a new one");
        }

        User user = userRepository.save(User.builder()
                .email(invitation.getEmail()).passwordHash(passwordEncoder.encode(password))
                .name(name).role(invitation.getRole())
                .handle(HandleGenerator.generate(name, userRepository::existsByHandle))
                .build());
        membershipRepository.save(Membership.builder()
                .user(user).tenant(invitation.getTenant()).status(MembershipStatus.ACTIVE)
                .invitedBy(invitation.getInvitedBy()).joinedAt(Instant.now())
                .build());
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitationRepository.save(invitation);
        return user;
    }

    @Transactional
    public void changeRole(UUID adminUserId, UUID membershipId, ChangeRoleRequest request) {
        EnterpriseProfile tenant = enterpriseProfileService.getEntityForUser(adminUserId);
        Membership membership = requireMembership(membershipId);
        requireSameTenant(membership.getTenant(), tenant);
        Role newRole = Role.fromWireValue(request.role());
        if (!INVITABLE_ROLES.contains(newRole)) {
            throw new BadRequestException("Can't set this role");
        }
        User user = membership.getUser();
        user.setRole(newRole);
        userRepository.save(user);
        auditService.record(tenant.getId(), adminUserId, AuditActions.MEMBER_ROLE_CHANGED,
                user.getName() + " -> " + newRole.wireValue());
    }

    @Transactional
    public void setSuspended(UUID adminUserId, UUID membershipId, boolean suspended) {
        EnterpriseProfile tenant = enterpriseProfileService.getEntityForUser(adminUserId);
        Membership membership = requireMembership(membershipId);
        requireSameTenant(membership.getTenant(), tenant);
        membership.setStatus(suspended ? MembershipStatus.SUSPENDED : MembershipStatus.ACTIVE);
        membershipRepository.save(membership);
    }

    /** CA2/G6: "remove recruiter transfers their pipeline." Postings and applications are already
     * tenant-scoped, not recruiter-scoped (no per-recruiter "created by" ownership exists
     * anywhere in the data model - see DECISIONS.md), so there is nothing to actually transfer:
     * every posting/applicant they touched already belongs to the tenant and stays visible to
     * every remaining member the instant this runs. Removal itself is what this method does. */
    @Transactional
    public void remove(UUID adminUserId, UUID membershipId) {
        EnterpriseProfile tenant = enterpriseProfileService.getEntityForUser(adminUserId);
        Membership membership = requireMembership(membershipId);
        requireSameTenant(membership.getTenant(), tenant);
        if (membership.getUser().getId().equals(adminUserId)) {
            throw new BadRequestException("You can't remove yourself");
        }
        String removedName = membership.getUser().getName();
        membershipRepository.delete(membership);
        auditService.record(tenant.getId(), adminUserId, AuditActions.MEMBER_REMOVED, removedName);
    }

    private void requireSameTenant(EnterpriseProfile a, EnterpriseProfile b) {
        if (!a.getId().equals(b.getId())) throw new AccessDeniedException("Not your team member");
    }

    private Membership requireMembership(UUID id) {
        return membershipRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Member not found: " + id));
    }

    private Invitation requireInvitation(UUID id) {
        return invitationRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Invitation not found: " + id));
    }

    private TeamMemberResponse toResponse(Membership m) {
        return new TeamMemberResponse(
                m.getId().toString(), m.getUser().getId().toString(), m.getUser().getName(), m.getUser().getEmail(),
                m.getUser().getRole().wireValue(), m.getStatus().wireValue(),
                m.getInvitedBy() == null ? null : m.getInvitedBy().getName(),
                m.getJoinedAt() == null ? m.getCreatedAt().toString() : m.getJoinedAt().toString());
    }

    private InvitationResponse toResponse(Invitation i) {
        return new InvitationResponse(
                i.getId().toString(), i.getEmail(), i.getRole().wireValue(),
                frontendUrl + "/invite/" + i.getToken(), i.getStatus().wireValue(),
                i.getExpiresAt().toString(), i.getInvitedBy().getName(), i.getCreatedAt().toString());
    }
}
