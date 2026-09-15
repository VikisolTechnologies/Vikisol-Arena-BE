package com.vikisol.arena.enterprise.repository;

import com.vikisol.arena.enterprise.entity.Invitation;
import com.vikisol.arena.enterprise.entity.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {
    Optional<Invitation> findByToken(String token);
    List<Invitation> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<Invitation> findByTenantIdAndStatus(UUID tenantId, InvitationStatus status);

    // DemoContentService - the real token for an invite just created via TeamService.invite(),
    // to feed straight into TeamService.acceptInvitation() without parsing InvitationResponse's
    // URL-shaped inviteLink field.
    Optional<Invitation> findByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);

    // DemoContentService.removeAll() - a demo tenant's invitations (pending or already accepted)
    // have to go before the tenant itself.
    void deleteByTenantId(UUID tenantId);
}
