package com.vikisol.arena.platform.admin;

import com.vikisol.arena.activities.entity.ActivityCatalogue;
import com.vikisol.arena.agent.client.AgentServiceTokenVerifier;
import com.vikisol.arena.audit.AuditActions;
import com.vikisol.arena.audit.AuditService;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.auth.service.GoogleIdTokenVerifier;
import com.vikisol.arena.common.embedding.EmbeddingProvider;
import com.vikisol.arena.common.embedding.HashingEmbeddingProvider;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.integration.provider.EmailProvider;
import com.vikisol.arena.integration.provider.MeetingLinkProvider;
import com.vikisol.arena.integration.provider.PhoneOtpProvider;
import com.vikisol.arena.integration.provider.WhatsAppProvider;
import com.vikisol.arena.jobs.entity.JobPosting;
import com.vikisol.arena.jobs.entity.PostingStatus;
import com.vikisol.arena.jobs.repository.JobPostingRepository;
import com.vikisol.arena.notifications.service.NotificationService;
import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.entity.PostStatus;
import com.vikisol.arena.posts.repository.PostRepository;
import com.vikisol.arena.search.SearchText;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * FE-API-GAPS rows 42, 44, 45, 47 and 49 (admin, B+): launch metrics, the content browser and
 * takedown, the activity catalogue, the Vikisol team list, and what Arena knows about Jenny and
 * its providers. Counts leave out demo content and staff accounts, as the public counts do.
 */
@Service
@RequiredArgsConstructor
public class AdminInsightsService {

    private static final int MAX_AREAS = 10;
    private static final int MAX_AREA_LENGTH = 80;

    private final NamedParameterJdbcTemplate jdbc;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final JobPostingRepository jobPostingRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final EmailProvider emailProvider;
    private final WhatsAppProvider whatsAppProvider;
    private final PhoneOtpProvider phoneOtpProvider;
    private final MeetingLinkProvider meetingLinkProvider;
    private final EmbeddingProvider embeddingProvider;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final AgentServiceTokenVerifier agentServiceTokenVerifier;

    // --- row 42: launch metrics ------------------------------------------------------------

    /**
     * Counts since `sinceDays` ago (all time when null). onboardingCompleted is null: nothing
     * records a finished onboarding yet. The return rates are the share of people who were active
     * (signed in or refreshed a session) the day after, and 7 days after, the day they signed up.
     * They count only people who signed up after activity tracking began (V41), and are null
     * until anyone in that group is old enough.
     */
    public record LaunchMetrics(Long signUps, Long onboardingCompleted, Long activitiesCreated, Long activitiesJoined,
                                Long activitiesCompleted, Long needsResolved, Double d1ReturnRate, Double d7ReturnRate,
                                Long reportsTotal, String since) {
    }

    @Transactional(readOnly = true)
    public LaunchMetrics launchMetrics(Integer sinceDays) {
        Instant since = sinceDays == null ? Instant.EPOCH : Instant.now().minus(Duration.ofDays(sinceDays));
        MapSqlParameterSource p = new MapSqlParameterSource("since", java.sql.Timestamp.from(since));
        String people = "u.demo_content = false and u.role <> 'PLATFORM_ADMIN'";
        return new LaunchMetrics(
                count("select count(*) from arena_users u where " + people + " and u.created_at >= :since", p),
                null,
                count("select count(*) from arena_posts where intent_type = 'ACTIVITY' and demo_content = false and created_at >= :since", p),
                count("""
                        select count(*) from arena_post_joins j join arena_posts p on p.id = j.post_id
                        where p.intent_type = 'ACTIVITY' and j.status = 'APPROVED' and j.demo_content = false and j.created_at >= :since
                        """, p),
                count("""
                        select count(distinct j.post_id) from arena_activity_attendance a join arena_post_joins j on j.id = a.join_id
                        where a.outcome_recorded_at is not null and a.demo_content = false and a.outcome_recorded_at >= :since
                        """, p),
                count("select count(*) from arena_need_completions where completed_at is not null and demo_content = false and completed_at >= :since", p),
                returnRate(1, people, p),
                returnRate(7, people, p),
                count("select count(*) from arena_moderation_items where demo_content = false and created_at >= :since", p),
                sinceDays == null ? null : since.toString());
    }

    private Double returnRate(int day, String people, MapSqlParameterSource p) {
        String cohort = """
                from arena_users u
                where %s and u.created_at >= :since
                  and (u.created_at at time zone 'utc')::date >= (select min(day) from arena_user_active_days)
                  and (u.created_at at time zone 'utc')::date <= (now() at time zone 'utc')::date - %d
                """.formatted(people, day);
        long size = count("select count(*) " + cohort, p);
        if (size == 0) return null;
        long returned = count("select count(*) " + cohort + " and exists (select 1 from arena_user_active_days d where d.user_id = u.id"
                + " and d.day = (u.created_at at time zone 'utc')::date + " + day + ")", p);
        return Math.round(1000.0 * returned / size) / 1000.0;
    }

    // --- row 44: content ---------------------------------------------------------------------

    public record ContentItem(String id, String kind, String title, String authorName, String area, String status,
                              String createdAt, long reportCount) {
    }

    @Transactional(readOnly = true)
    public Page<ContentItem> content(String kind, String query, Pageable pageable) {
        Set<String> kinds = kind == null || kind.isBlank() ? Set.of("activity", "need", "job") : Set.of(kind.trim().toLowerCase(Locale.ROOT));
        if (!Set.of("activity", "need", "job").containsAll(kinds)) throw new BadRequestException("kind must be one of activity, need, job");
        List<String> parts = new ArrayList<>();
        List<String> intents = new ArrayList<>();
        if (kinds.contains("activity")) intents.add("ACTIVITY");
        if (kinds.contains("need")) intents.add("ASK");
        if (!intents.isEmpty()) {
            parts.add("""
                    select p.id, case when p.intent_type = 'ACTIVITY' then 'activity' else 'need' end as kind,
                           coalesce(p.title, left(p.body, 80)) as title, u.name as author_name, p.location_text as area,
                           lower(p.status) as status, p.created_at,
                           (select count(*) from arena_moderation_items m left join arena_rooms r on r.id = m.room_id
                            where m.post_id = p.id or r.post_id = p.id) as report_count
                    from arena_posts p join arena_users u on u.id = p.author_user_id
                    where p.intent_type in (:intents)
                    """);
        }
        if (kinds.contains("job")) {
            parts.add("""
                    select j.id, 'job' as kind, j.title, e.company_name as author_name, j.location as area,
                           lower(j.status) as status, j.created_at,
                           (select count(*) from arena_moderation_items m where m.job_posting_id = j.id) as report_count
                    from arena_job_postings j join arena_enterprise_profiles e on e.id = j.enterprise_id
                    """);
        }
        String q = query == null || query.isBlank() ? null : SearchText.likePattern(query.trim().toLowerCase(Locale.ROOT));
        String where = q == null ? "" : " where lower(c.title) like :q or lower(c.author_name) like :q";
        String union = "(" + String.join(" union all ", parts) + ") c" + where;
        MapSqlParameterSource p = new MapSqlParameterSource("intents", intents.isEmpty() ? List.of("NONE") : intents)
                .addValue("q", q).addValue("limit", pageable.getPageSize()).addValue("offset", pageable.getOffset());
        long total = count("select count(*) from " + union, p);
        List<ContentItem> rows = jdbc.query("select * from " + union + " order by c.created_at desc limit :limit offset :offset", p,
                (rs, i) -> new ContentItem(rs.getString("id"), rs.getString("kind"), rs.getString("title"), rs.getString("author_name"),
                        rs.getString("area"), rs.getString("status"), rs.getTimestamp("created_at").toInstant().toString(),
                        rs.getLong("report_count")));
        return new PageImpl<>(rows, pageable, total);
    }

    // Takes an activity, need or job down: the post is cancelled or the job closed, its open
    // reports are resolved, the owner is told why, and the action is audited with the reason.
    @Transactional
    public ContentItem takedown(UUID adminId, UUID id, String reason) {
        String why = reason.trim();
        Post post = postRepository.findById(id).orElse(null);
        if (post != null) {
            post.setStatus(PostStatus.CANCELLED);
            post.setCancelReason("Removed by Arena's team");
            postRepository.save(post);
            resolveReports("post_id = :id or room_id in (select r.id from arena_rooms r where r.post_id = :id)", id, adminId);
            notificationService.notifySystem(post.getAuthorUser(), NotificationService.SAFETY, "Your post was removed",
                    "Arena's team removed \"" + preview(post.getTitle() != null ? post.getTitle() : post.getBody()) + "\": " + why);
            auditService.record(null, adminId, AuditActions.CONTENT_TAKEDOWN, "post " + id, why);
            return new ContentItem(id.toString(), post.getIntentType() == com.vikisol.arena.posts.entity.PostIntentType.ACTIVITY ? "activity" : "need",
                    post.getTitle() != null ? post.getTitle() : preview(post.getBody()), post.getAuthorUser().getName(),
                    post.getLocationText(), "cancelled", post.getCreatedAt().toString(),
                    count("select count(*) from arena_moderation_items m left join arena_rooms r on r.id = m.room_id where m.post_id = :id or r.post_id = :id",
                            new MapSqlParameterSource("id", id)));
        }
        JobPosting job = jobPostingRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Content not found: " + id));
        job.setStatus(PostingStatus.CLOSED);
        jobPostingRepository.save(job);
        resolveReports("job_posting_id = :id", id, adminId);
        notificationService.notifyJob(job.getEnterprise().getUser(), "A job was removed",
                "Arena's team closed \"" + preview(job.getTitle()) + "\": " + why);
        auditService.record(job.getEnterprise().getId(), adminId, AuditActions.CONTENT_TAKEDOWN, "job " + id, why);
        return new ContentItem(id.toString(), "job", job.getTitle(), job.getEnterprise().getCompanyName(), job.getLocation(),
                "closed", job.getCreatedAt().toString(), count("select count(*) from arena_moderation_items where job_posting_id = :id",
                new MapSqlParameterSource("id", id)));
    }

    private void resolveReports(String condition, UUID id, UUID adminId) {
        postRepository.flush();
        jdbc.update("update arena_moderation_items set status = 'TAKEN_DOWN', resolved_by_user_id = :admin, resolved_at = now()"
                + " where status = 'PENDING' and (" + condition + ")", new MapSqlParameterSource("id", id).addValue("admin", adminId));
    }

    // --- row 45: catalogue -----------------------------------------------------------------

    public record ActivityType(String category, List<String> subtypes) {
    }

    public List<ActivityType> activityTypes() {
        return ActivityCatalogue.catalogue().entrySet().stream().map(e -> new ActivityType(e.getKey(), e.getValue())).toList();
    }

    // --- row 49: Vikisol team ----------------------------------------------------------------

    // Never includes passwords, 2FA secrets or codes.
    public record StaffMember(String id, String name, String email, boolean twoFactorEnabled, List<String> launchAreas,
                              String lastActiveAt) {
    }

    @Transactional(readOnly = true)
    public List<StaffMember> team() {
        return userRepository.findByRoleOrderByNameAsc(Role.PLATFORM_ADMIN).stream()
                .filter(u -> u.getDeletedAt() == null)
                .map(this::staff).toList();
    }

    @Transactional
    public StaffMember setLaunchAreas(UUID adminId, UUID staffId, List<String> areas) {
        User u = userRepository.findById(staffId).filter(x -> x.getRole() == Role.PLATFORM_ADMIN && x.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Staff account not found: " + staffId));
        LinkedHashSet<String> clean = new LinkedHashSet<>();
        for (String a : areas) {
            String area = a == null ? "" : a.trim().replaceAll("\\s+", " ");
            if (area.isEmpty()) continue;
            if (area.length() > MAX_AREA_LENGTH) throw new BadRequestException("Each area can be at most " + MAX_AREA_LENGTH + " characters");
            clean.add(area);
        }
        if (clean.size() > MAX_AREAS) throw new BadRequestException("Up to " + MAX_AREAS + " areas");
        MapSqlParameterSource p = new MapSqlParameterSource("u", staffId);
        jdbc.update("delete from arena_staff_launch_areas where user_id = :u", p);
        for (String area : clean) {
            jdbc.update("insert into arena_staff_launch_areas (user_id, area) values (:u, :a)", new MapSqlParameterSource("u", staffId).addValue("a", area));
        }
        auditService.record(null, adminId, AuditActions.STAFF_AREAS_SET, u.getName() + " (" + u.getId() + ")", String.join(", ", clean));
        return staff(u);
    }

    private StaffMember staff(User u) {
        List<String> areas = jdbc.queryForList("select area from arena_staff_launch_areas where user_id = :u order by area",
                new MapSqlParameterSource("u", u.getId()), String.class);
        return new StaffMember(u.getId().toString(), u.getName(), u.getEmail(), u.isTotpEnabled(), areas,
                u.getLastActiveAt() == null ? null : u.getLastActiveAt().toString());
    }

    // --- row 47: Jenny and providers ---------------------------------------------------------

    /** Whether each outside service is set up. Nothing is called and no key is shown. */
    public record ProviderStatus(String name, String purpose, boolean configured) {
    }

    public List<ProviderStatus> providers() {
        return List.of(
                new ProviderStatus("jenny", "JennySol gateway service tokens", agentServiceTokenVerifier.isConfigured()),
                new ProviderStatus("email", "Resend email", emailProvider.isConfigured()),
                new ProviderStatus("sms", "MSG91 phone codes", phoneOtpProvider.isConfigured()),
                new ProviderStatus("whatsapp", "WhatsApp Business messages", whatsAppProvider.isConfigured()),
                new ProviderStatus("meetings", "Teams meeting links", meetingLinkProvider.isConfigured()),
                new ProviderStatus("embeddings", "OpenAI embeddings (otherwise local word hashing)",
                        !(embeddingProvider instanceof HashingEmbeddingProvider)),
                new ProviderStatus("google", "Google sign-in", googleIdTokenVerifier.isConfigured()));
    }

    private long count(String sql, MapSqlParameterSource p) {
        Long n = jdbc.queryForObject(sql, p, Long.class);
        return n == null ? 0 : n;
    }

    private static String preview(String s) {
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }
}
