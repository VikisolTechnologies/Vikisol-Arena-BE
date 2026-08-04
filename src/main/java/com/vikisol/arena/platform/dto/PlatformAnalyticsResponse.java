package com.vikisol.arena.platform.dto;

import java.util.Map;

public record PlatformAnalyticsResponse(
        int tenantsTotal,
        long tenantsSuspended,
        Map<String, Long> tenantsByPlan,
        int usersTotal,
        Map<String, Long> usersByRole,
        int postingsTotal,
        long postingsOpen,
        int applicationsTotal,
        int interviewsTotal,
        long newTenantsLast7d,
        long newUsersLast7d
) {
}
