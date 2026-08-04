package com.vikisol.arena.platform.service;

import com.vikisol.arena.applications.repository.ApplicationRepository;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// PA5 (platform analytics). Tenant/user breakdowns are computed in-memory over findAll() rather
// than group-by queries - at this scale (a demo-stage platform, not millions of tenants) that's
// simpler to read and verify than several hand-written aggregate queries, matching
// AdminDashboardService's existing "stream over what's already loaded" style for CA1.
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
        List<EnterpriseProfile> tenants = enterpriseProfileRepository.findAll();
        List<User> users = userRepository.findAll();

        Map<String, Long> tenantsByPlan = tenants.stream()
                .collect(Collectors.groupingBy(t -> t.getPlan().wireValue(), Collectors.counting()));
        Map<String, Long> usersByRole = users.stream()
                .collect(Collectors.groupingBy(u -> u.getRole().wireValue(), Collectors.counting()));
        long tenantsSuspended = tenants.stream().filter(t -> t.getStatus() == TenantStatus.SUSPENDED).count();

        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        long newTenants7d = enterpriseProfileRepository.countByCreatedAtAfter(sevenDaysAgo);
        long newUsers7d = userRepository.countByCreatedAtAfter(sevenDaysAgo);

        long postingsOpen = jobPostingRepository.countByStatus(PostingStatus.OPEN);

        return new PlatformAnalyticsResponse(
                tenants.size(), tenantsSuspended, tenantsByPlan,
                users.size(), usersByRole,
                (int) jobPostingRepository.count(), postingsOpen,
                (int) applicationRepository.count(), (int) interviewRepository.count(),
                newTenants7d, newUsers7d);
    }
}
