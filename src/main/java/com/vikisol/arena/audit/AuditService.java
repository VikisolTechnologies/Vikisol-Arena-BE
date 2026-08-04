package com.vikisol.arena.audit;

import com.vikisol.arena.audit.entity.AuditEvent;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Called explicitly at each meaningful action site (see DECISIONS.md for why not an aspect).
// Never allowed to fail the action it's recording for - a broken audit write shouldn't block
// a recruiter from posting a job, same "best-effort, catch and log" posture the codebase
// already uses for notifications/email (see AuthService's welcome-email try/catch).
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditEventRepository auditEventRepository;
    private final UserRepository userRepository;
    private final EnterpriseProfileRepository enterpriseProfileRepository;

    @Transactional
    public void record(UUID tenantId, UUID actorUserId, String action, String target, String metadata) {
        try {
            User actorRef = userRepository.getReferenceById(actorUserId);
            EnterpriseProfile tenantRef = tenantId == null ? null : enterpriseProfileRepository.getReferenceById(tenantId);
            auditEventRepository.save(AuditEvent.builder()
                    .tenant(tenantRef).actor(actorRef).action(action).target(target).metadata(metadata)
                    .build());
        } catch (Exception e) {
            log.warn("Audit write failed for action={} tenantId={}: {}", action, tenantId, e.getMessage());
        }
    }

    public void record(UUID tenantId, UUID actorUserId, String action, String target) {
        record(tenantId, actorUserId, action, target, null);
    }

    // CA3 (audit log): filterable by actor/action/date, chronological. actorId/action stay
    // nullable ("no filter"); since is never passed through as null (see
    // AuditEventRepository.search()'s comment) - Instant.EPOCH stands in for "no lower bound".
    @Transactional(readOnly = true)
    public PagedResponse<AuditEventResponse> search(UUID tenantId, UUID actorId, String action, Instant since, Pageable pageable) {
        return PagedResponse.of(
                auditEventRepository.search(tenantId, actorId, action, since == null ? Instant.EPOCH : since, pageable),
                this::toResponse);
    }

    // CA3 "export CSV" - same filters as search(), unpaged. Capped rather than truly unbounded so
    // a very long-lived tenant can't accidentally request an unbounded result set into memory.
    @Transactional(readOnly = true)
    public List<AuditEventResponse> exportAll(UUID tenantId, UUID actorId, String action, Instant since) {
        return auditEventRepository.search(tenantId, actorId, action, since == null ? Instant.EPOCH : since,
                        org.springframework.data.domain.PageRequest.of(0, 5000))
                .map(this::toResponse).getContent();
    }

    // PA1 dashboard: latest activity across every tenant, not just one - see
    // AuditEventRepository.findAllByOrderByCreatedAtDesc()'s comment.
    @Transactional(readOnly = true)
    public List<AuditEventResponse> recentGlobal(int limit) {
        return auditEventRepository.findAllByOrderByCreatedAtDesc(org.springframework.data.domain.PageRequest.of(0, limit))
                .map(this::toResponse).getContent();
    }

    private AuditEventResponse toResponse(AuditEvent e) {
        return new AuditEventResponse(
                e.getId().toString(), e.getActor().getName(), e.getAction(), e.getTarget(), e.getMetadata(),
                e.getCreatedAt().toString());
    }
}
