package com.vikisol.arena.audit;

import com.vikisol.arena.audit.entity.AuditEvent;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
