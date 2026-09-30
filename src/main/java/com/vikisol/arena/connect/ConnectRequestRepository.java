package com.vikisol.arena.connect;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConnectRequestRepository extends JpaRepository<ConnectRequest, UUID> {

    Optional<ConnectRequest> findByTenantIdAndCandidateId(UUID tenantId, UUID candidateUserId);

    @EntityGraph(attributePaths = {"tenant", "job"})
    Page<ConnectRequest> findByCandidateIdOrderByCreatedAtDescIdDesc(UUID candidateUserId, Pageable pageable);

    boolean existsByTenantIdAndCandidateIdAndStatus(UUID tenantId, UUID candidateUserId, ConnectRequest.Status status);

    List<ConnectRequest> findByCandidateId(UUID candidateUserId);

    List<ConnectRequest> findBySenderId(UUID senderUserId);
}
