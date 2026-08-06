package com.vikisol.arena.platform.service;

import com.vikisol.arena.applications.repository.ApplicationRepository;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.enterprise.entity.TenantStatus;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import com.vikisol.arena.interviews.repository.InterviewRepository;
import com.vikisol.arena.jobs.entity.PostingStatus;
import com.vikisol.arena.jobs.repository.JobPostingRepository;
import com.vikisol.arena.platform.dto.PlatformAnalyticsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.stream.Collectors;

// PA5 (platform analytics). Tenant/user breakdowns are SQL COUNT/GROUP BY aggregates - this used
// to load every tenant and every user row into memory (EnterpriseProfileRepository.findAll(),
// UserRepository.findAll()) just to count them in Java, on every dashboard load.
@Service
@RequiredArgsConstructor
public class PlatformAnalyticsService {

    private final EnterpriseProfileRepository enterpriseProfileRepository;
    private final UserRepository userRepository;
    private final JobPostingRepository jobPostingRepository;
    private final ApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;

    @Transactional(readOnly = true)
    public PlatformAnalyticsResponse getAnalytics() {
        Map<String, Long> tenantsByPlan = enterpriseProfileRepository.countByPlanGrouped().stream()
                .collect(Collectors.toMap(pc -> pc.getPlan().wireValue(), EnterpriseProfileRepository.PlanCount::getCount));
        Map<String, Long> usersByRole = userRepository.countByRoleGrouped().stream()
                .collect(Collectors.toMap(rc -> rc.getRole().wireValue(), UserRepository.RoleCount::getCount));

        long tenantsSuspended = enterpriseProfileRepository.countByStatus(TenantStatus.SUSPENDED);

        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        long newTenants7d = enterpriseProfileRepository.countByCreatedAtAfter(sevenDaysAgo);
        long newUsers7d = userRepository.countByCreatedAtAfter(sevenDaysAgo);

        long postingsOpen = jobPostingRepository.countByStatus(PostingStatus.OPEN);

        return new PlatformAnalyticsResponse(
                (int) enterpriseProfileRepository.count(), tenantsSuspended, tenantsByPlan,
                (int) userRepository.count(), usersByRole,
                (int) jobPostingRepository.count(), postingsOpen,
                (int) applicationRepository.count(), (int) interviewRepository.count(),
                newTenants7d, newUsers7d);
    }
}
