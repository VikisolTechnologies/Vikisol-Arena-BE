package com.vikisol.arena.privacy;

import com.vikisol.arena.platform.service.FeatureFlagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Architect decision 30 Sep 2026 (DECISIONS.md): candidate data on a role is deleted 12 months
 * after the role closes. Behind the candidate_retention_enabled flag, which ships OFF. While it
 * is off the daily run is a dry run: it only counts what it would delete and logs the counts,
 * never names, emails or text. GET /admin/retention shows the same counts on demand, so the
 * architect can check them before the flag is switched on.
 *
 * "Candidate data on a role" is every application to a posting closed more than 12 months ago,
 * with its screening answers, must-have evidence and assessments, recruiter notes, timeline,
 * interviews and interview slots. The posting itself is the company's and stays.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateRetentionService {

    public static final String FLAG = "candidate_retention_enabled";
    static final Duration RETENTION = Duration.ofDays(365);

    private static final String EXPIRED_POSTINGS =
            "select id from arena_job_postings where status = 'CLOSED' and closed_at < :cutoff";
    private static final String EXPIRED_APPLICATIONS =
            "select id from arena_applications where job_posting_id in (" + EXPIRED_POSTINGS + ")";
    private static final String EXPIRED_INTERVIEWS =
            "select id from arena_interviews where application_id in (" + EXPIRED_APPLICATIONS + ")";

    private final NamedParameterJdbcTemplate jdbc;
    private final FeatureFlagService featureFlagService;

    /** Counts only: what a run would delete right now. Nothing personal. */
    public record Report(boolean enabled, boolean deleted, String cutoff, long postings, long applications,
                         long screeningAnswers, long evidence, long notes, long events, long interviews,
                         long interviewSlots) {
    }

    @Transactional(readOnly = true)
    public Report report() {
        return counts(featureFlagService.isEnabled(FLAG), false, cutoff(Instant.now()));
    }

    // Daily at 03:40 UTC (app.retention.cron). A dry run unless the flag is on.
    @Scheduled(cron = "${app.retention.cron:0 40 3 * * *}", zone = "UTC")
    @Transactional
    public Report run() {
        Instant cutoff = cutoff(Instant.now());
        boolean enabled = featureFlagService.isEnabled(FLAG);
        Report before = counts(enabled, false, cutoff);
        if (!enabled) {
            log.info("Candidate retention dry run (flag off): would delete {}", summary(before));
            return before;
        }
        if (before.applications() == 0) return before;
        MapSqlParameterSource p = new MapSqlParameterSource("cutoff", java.sql.Timestamp.from(cutoff));
        jdbc.update("delete from arena_interview_slots where interview_id in (" + EXPIRED_INTERVIEWS + ")", p);
        jdbc.update("delete from arena_interviews where application_id in (" + EXPIRED_APPLICATIONS + ")", p);
        // Answers, evidence, notes and events go with the application (ON DELETE CASCADE, V26/V29).
        jdbc.update("delete from arena_applications where id in (" + EXPIRED_APPLICATIONS + ")", p);
        Report done = new Report(true, true, before.cutoff(), before.postings(), before.applications(),
                before.screeningAnswers(), before.evidence(), before.notes(), before.events(), before.interviews(),
                before.interviewSlots());
        log.info("Candidate retention deleted {}", summary(done));
        return done;
    }

    static Instant cutoff(Instant now) {
        return now.minus(RETENTION);
    }

    private Report counts(boolean enabled, boolean deleted, Instant cutoff) {
        MapSqlParameterSource p = new MapSqlParameterSource("cutoff", java.sql.Timestamp.from(cutoff));
        return new Report(enabled, deleted, cutoff.toString(),
                count("select count(distinct job_posting_id) from arena_applications where job_posting_id in (" + EXPIRED_POSTINGS + ")", p),
                count("select count(*) from (" + EXPIRED_APPLICATIONS + ") a", p),
                count("select count(*) from arena_application_answers where application_id in (" + EXPIRED_APPLICATIONS + ")", p),
                count("select count(*) from arena_application_evidence where application_id in (" + EXPIRED_APPLICATIONS + ")", p),
                count("select count(*) from arena_application_notes where application_id in (" + EXPIRED_APPLICATIONS + ")", p),
                count("select count(*) from arena_application_events where application_id in (" + EXPIRED_APPLICATIONS + ")", p),
                count("select count(*) from (" + EXPIRED_INTERVIEWS + ") i", p),
                count("select count(*) from arena_interview_slots where interview_id in (" + EXPIRED_INTERVIEWS + ")", p));
    }

    private long count(String sql, MapSqlParameterSource p) {
        Long n = jdbc.queryForObject(sql, p, Long.class);
        return n == null ? 0 : n;
    }

    private static String summary(Report r) {
        return "applications=" + r.applications() + " postings=" + r.postings() + " answers=" + r.screeningAnswers()
                + " evidence=" + r.evidence() + " notes=" + r.notes() + " events=" + r.events()
                + " interviews=" + r.interviews() + " slots=" + r.interviewSlots() + " (closed before " + r.cutoff() + ")";
    }
}
