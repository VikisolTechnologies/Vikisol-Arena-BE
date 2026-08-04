package com.vikisol.arena.platform.dto;

import com.vikisol.arena.audit.AuditEventResponse;

import java.util.List;

public record PlatformDashboardResponse(
        int tenantsTotal,
        long tenantsSuspended,
        long usersTotal,
        long moderationPending,
        List<AuditEventResponse> recentActivity
) {
}
