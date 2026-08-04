package com.vikisol.arena.platform.service;

import com.vikisol.arena.audit.AuditService;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.enterprise.entity.TenantStatus;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import com.vikisol.arena.platform.dto.PlatformDashboardResponse;
import com.vikisol.arena.platform.entity.ModerationStatus;
import com.vikisol.arena.platform.repository.ModerationItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// PA1's landing page - top-line counts plus the same cross-tenant recent-activity feed the
// founder flagged as the sales-pitch surface (see DECISIONS.md / the audit log being CA3's
// equivalent here, just global instead of per-tenant).
@Service
@RequiredArgsConstructor
public class PlatformDashboardService {

    private final EnterpriseProfileRepository enterpriseProfileRepository;
    private final UserRepository userRepository;
    private final ModerationItemRepository moderationItemRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PlatformDashboardResponse getDashboard() {
        long tenantsTotal = enterpriseProfileRepository.count();
        long suspended = enterpriseProfileRepository.countByStatus(TenantStatus.SUSPENDED);
        long usersTotal = userRepository.count();
        long pendingModeration = moderationItemRepository.countByStatus(ModerationStatus.PENDING);
        return new PlatformDashboardResponse((int) tenantsTotal, suspended, usersTotal, pendingModeration,
                auditService.recentGlobal(15));
    }
}
