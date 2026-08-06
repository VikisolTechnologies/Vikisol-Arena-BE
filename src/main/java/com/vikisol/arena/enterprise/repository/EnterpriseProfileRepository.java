package com.vikisol.arena.enterprise.repository;

import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.entity.Plan;
import com.vikisol.arena.enterprise.entity.TenantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnterpriseProfileRepository extends JpaRepository<EnterpriseProfile, UUID> {
    Optional<EnterpriseProfile> findByUserId(UUID userId);
    long countByStatus(TenantStatus status);
    long countByCreatedAtAfter(Instant since);

    // PA5 (platform analytics): tenant-count-by-plan breakdown as a SQL GROUP BY instead of
    // PlatformAnalyticsService loading every tenant row into memory and counting in Java.
    @Query("select t.plan as plan, count(t) as count from EnterpriseProfile t group by t.plan")
    List<PlanCount> countByPlanGrouped();

    // PA1 (tenants list, searchable by company name). `q` is always a non-null, possibly-empty
    // string from the service layer, never a literal null - see AuditEventRepository.search()'s
    // comment for why a `:q is null or ... like ...` shape is unsafe with Postgres/JDBC here.
    @Query("""
            select t from EnterpriseProfile t where (:q = '' or lower(t.companyName) like lower(concat('%', :q, '%')))
            order by t.createdAt desc
            """)
    Page<EnterpriseProfile> search(@Param("q") String q, Pageable pageable);

    interface PlanCount {
        Plan getPlan();
        long getCount();
    }
}
