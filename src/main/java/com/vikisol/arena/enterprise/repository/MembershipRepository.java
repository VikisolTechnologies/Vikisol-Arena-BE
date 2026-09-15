package com.vikisol.arena.enterprise.repository;

import com.vikisol.arena.enterprise.entity.Membership;
import com.vikisol.arena.enterprise.entity.MembershipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {
    Optional<Membership> findByUserId(UUID userId);

    // TeamService.listMembers() reads member.getUser() and member.getInvitedBy() for every row -
    // both are single-valued (@ManyToOne) associations, so eagerly fetching them here is one query
    // instead of up to two lazy loads per team member.
    @EntityGraph(attributePaths = {"user", "invitedBy"})
    List<Membership> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Page<Membership> findByTenantId(UUID tenantId, Pageable pageable);

    // AdminDashboardService.getDashboard() reads member.getUser() for every member in the loop
    // that builds per-recruiter activity rows - eagerly fetching it here avoids one lazy load per
    // team member on top of the audit-event batching fix in AuditEventRepository.
    @EntityGraph(attributePaths = "user")
    List<Membership> findByTenantIdAndStatus(UUID tenantId, MembershipStatus status);

    long countByTenantIdAndStatus(UUID tenantId, MembershipStatus status);

    // DemoContentService.removeAll() - a tenant's memberships go before the EnterpriseProfile
    // (FK) and before its admin User (also FK'd from here).
    void deleteByTenantId(UUID tenantId);
}
