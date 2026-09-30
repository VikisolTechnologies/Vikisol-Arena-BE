package com.vikisol.arena.privacy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikisol.arena.common.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data export and erasure over every table the feature/be-fe-gaps work added (V22-V37). The
 * architect's rule (legal, DPDP): export includes the person's own entries; erasure deletes them.
 * <p>
 * Export: everything the person wrote (answers, notes, feedback, disputes, offers, cover notes,
 * recruiter notes and assessments, messages on stage changes, requests, preferences, evidence),
 * plus the private records about them that the product already shows them (feedback received,
 * their attendance, their applications' timeline).
 * <p>
 * Erasure: deletes or blanks all of that, and the private records about them kept by others
 * (recruiter notes, assessments and interview feedback on their applications, feedback about
 * them, their attendance and disputes). Shared content stays where removing it would break
 * someone else's record (their posts, a project's milestone titles, a company's connect request
 * note); those keep no link to the person.
 * <p>
 * Plain SQL on purpose: one place lists every table, so a reviewer can check it against the
 * migrations line by line (PersonalDataTest does the same for each table).
 */
@Service
@RequiredArgsConstructor
public class PersonalDataService {

    private final NamedParameterJdbcTemplate jdbc;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final jakarta.persistence.EntityManager entityManager;

    // Export sections in a stable order. Each is a list of rows (column -> value).
    private static final Map<String, String> EXPORT = new LinkedHashMap<>();

    static {
        String myCandidate = "(select c.id from arena_candidate_profiles c where c.user_id = :u)";
        String myApplications = "(select a.id from arena_applications a where a.candidate_id in " + myCandidate + ")";
        // Activities (V23, V30)
        EXPORT.put("activityQuestionsAsked", "select q.post_id, q.text, q.required from arena_activity_questions q join arena_posts p on p.id = q.post_id where p.author_user_id = :u");
        EXPORT.put("activityAnswers", "select q.post_id, q.text as question, a.answer, a.created_at from arena_activity_answers a join arena_activity_questions q on q.id = a.question_id where a.user_id = :u");
        EXPORT.put("activityWaitlist", "select post_id, status, joined_at, promoted_at from arena_activity_waitlist where user_id = :u");
        EXPORT.put("joinNotes", "select post_id, status, note, decision_note as host_note_to_you from arena_post_joins where user_id = :u and (note is not null or decision_note is not null)");
        EXPORT.put("hostDecisionNotesWritten", "select j.post_id, j.decision_note from arena_post_joins j join arena_posts p on p.id = j.post_id where p.author_user_id = :u and j.decision_note is not null");
        EXPORT.put("attendance", "select j.post_id, a.checked_in_at, a.outcome_recorded_at, a.joiner_attended, a.joiner_confirmed_at, a.dispute_status, a.dispute_reason, a.disputed_at, a.dispute_resolution_note, a.dispute_resolved_at from arena_activity_attendance a join arena_post_joins j on j.id = a.join_id where j.user_id = :u");
        EXPORT.put("activityFeedbackGiven", "select post_id, to_user_id, join_again, text, created_at from arena_activity_feedback where from_user_id = :u");
        EXPORT.put("activityFeedbackReceived", "select post_id, join_again, text, created_at from arena_activity_feedback where to_user_id = :u");
        EXPORT.put("emergencyContacts", "select post_id, name, phone, created_at from arena_activity_emergency_contacts where user_id = :u");
        EXPORT.put("reminders", "select post_id, minutes_before, remind_at, sent_at from arena_post_reminders where user_id = :u");
        // Needs & offers (V24, V32)
        EXPORT.put("needDetails", "select d.post_id, d.category, d.preferred_time, d.urgency, d.help_type, d.answers_json, d.offer_days_json, d.offer_limit, d.proof_url from arena_need_details d join arena_posts p on p.id = d.post_id where p.author_user_id = :u");
        EXPORT.put("needResponses", "select post_id, message, status, created_at, decided_at from arena_post_responses where user_id = :u");
        EXPORT.put("needCompletionNotes", "select r.post_id, c.responder_note as note, c.responder_confirmed_at as confirmed_at from arena_need_completions c join arena_post_responses r on r.id = c.response_id where r.user_id = :u "
                + "union all select r.post_id, c.owner_note, c.owner_confirmed_at from arena_need_completions c join arena_post_responses r on r.id = c.response_id join arena_posts p on p.id = r.post_id where p.author_user_id = :u");
        // Career (V25, V33)
        EXPORT.put("careerProfile", "select intent, desired_role, experience_level, work_mode, notice_period, compensation_visibility, expected_min, expected_max, open_to_work, published_at, current_company, work_status, last_working_day, experience_months, role_family, current_ctc_fixed, current_ctc_variable, negotiable, details_json, visibility_json from arena_career_profiles where user_id = :u");
        EXPORT.put("careerLocations", "select l.location from arena_career_locations l join arena_career_profiles c on c.id = l.career_id where c.user_id = :u");
        // Jobs & applications (V26, V29, V34)
        EXPORT.put("applications", "select a.id, j.title as job, a.stage, a.cover_note, a.include_ctc, a.show_outcome, a.applied_at from arena_applications a join arena_job_postings j on j.id = a.job_posting_id where a.candidate_id in " + myCandidate);
        EXPORT.put("screeningAnswers", "select a.application_id, q.text as question, a.answer from arena_application_answers a join arena_job_screening_questions q on q.id = a.question_id where a.application_id in " + myApplications);
        EXPORT.put("mustHaveEvidence", "select e.application_id, r.text as requirement, e.candidate_evidence from arena_application_evidence e join arena_job_requirements r on r.id = e.requirement_id where e.application_id in " + myApplications + " and e.candidate_evidence is not null");
        EXPORT.put("applicationTimeline", "select application_id, type, stage, message, created_at from arena_application_events where application_id in " + myApplications);
        EXPORT.put("recruiterNotesWritten", "select application_id, text, created_at from arena_application_notes where author_user_id = :u");
        EXPORT.put("assessmentsWritten", "select e.application_id, r.text as requirement, e.assessment, e.assessment_note, e.assessed_at from arena_application_evidence e join arena_job_requirements r on r.id = e.requirement_id where e.assessed_by_user_id = :u");
        EXPORT.put("stageMessagesWritten", "select application_id, stage, message, created_at from arena_application_events where actor_user_id = :u and message is not null");
        EXPORT.put("savedJobs", "select s.posting_id, j.title, s.created_at from arena_saved_jobs s join arena_job_postings j on j.id = s.posting_id where s.user_id = :u");
        // Projects (V28, V36)
        EXPORT.put("projectMemberships", "select m.post_id, r.title as role, m.message from arena_project_members m left join arena_project_roles r on r.id = m.role_id where m.user_id = :u");
        EXPORT.put("projectMilestonesAdded", "select post_id, title, done, done_at from arena_project_milestones where created_by_user_id = :u");
        EXPORT.put("projectContributions", "select c.post_id, d.outcome, d.completed_at from arena_project_contributors c left join arena_project_details d on d.post_id = c.post_id where c.user_id = :u");
        // People app (V37)
        EXPORT.put("connectRequestsReceived", "select e.company_name, c.note, c.status, c.created_at, c.decided_at from arena_connect_requests c join arena_enterprise_profiles e on e.id = c.tenant_id where c.candidate_user_id = :u");
        EXPORT.put("connectRequestsSent", "select e.company_name, c.note, c.status, c.created_at from arena_connect_requests c join arena_enterprise_profiles e on e.id = c.tenant_id where c.sender_user_id = :u");
        EXPORT.put("notificationPreferences", "select activity, need, job, message, jenny, marketing from arena_notification_preferences where user_id = :u");
        // V41: the days they were active (sign-in or session refresh), and staff launch areas.
        EXPORT.put("activeDays", "select day from arena_user_active_days where user_id = :u order by day");
        EXPORT.put("staffLaunchAreas", "select area from arena_staff_launch_areas where user_id = :u");
        EXPORT.put("reportEvidence", "select id as report_id, reason, evidence_json, created_at from arena_moderation_items where reporter_user_id = :u and evidence_json <> '[]'");
    }

    // Both export endpoints come through here; the time is shown to admins (row 51 flags).
    @Transactional
    public Map<String, List<Map<String, Object>>> export(UUID userId) {
        entityManager.flush(); // SQL below must see this transaction's pending JPA writes
        MapSqlParameterSource p = new MapSqlParameterSource("u", userId);
        var user = entityManager.find(com.vikisol.arena.auth.entity.User.class, userId);
        if (user != null) user.setLastDataExportAt(java.time.Instant.now());
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        EXPORT.forEach((section, sql) -> out.put(section, jdbc.queryForList(sql, p).stream().map(PersonalDataService::plain).toList()));
        return out;
    }

    // Deletes (or blanks, where a row is someone else's record too) the person's entries in the
    // new tables. Runs inside the caller's erasure transaction.
    @Transactional
    public void erase(UUID userId) {
        entityManager.flush(); // SQL below must see this transaction's pending JPA writes
        MapSqlParameterSource p = new MapSqlParameterSource("u", userId);
        String myJoins = "(select j.id from arena_post_joins j where j.user_id = :u)";
        String myPosts = "(select id from arena_posts where author_user_id = :u)";
        String myResponses = "(select r.id from arena_post_responses r where r.user_id = :u)";
        String myApplications = "(select a.id from arena_applications a where a.candidate_id in (select c.id from arena_candidate_profiles c where c.user_id = :u))";

        // Evidence files uploaded with reports: the files go, the report record stays for safety.
        for (Map<String, Object> row : jdbc.queryForList("select evidence_json from arena_moderation_items where reporter_user_id = :u and evidence_json <> '[]'", p)) {
            for (String url : readList((String) row.get("evidence_json"))) fileStorageService.delete(url);
        }
        List<String> statements = List.of(
                // Activities
                "delete from arena_activity_answers where user_id = :u",
                "delete from arena_activity_waitlist where user_id = :u",
                "delete from arena_activity_attendance where join_id in " + myJoins,
                "update arena_activity_attendance set dispute_resolved_by_user_id = null where dispute_resolved_by_user_id = :u",
                "update arena_post_joins set note = null, decision_note = null where user_id = :u",
                "update arena_post_joins set decision_note = null where post_id in " + myPosts,
                "delete from arena_activity_feedback where from_user_id = :u or to_user_id = :u",
                "delete from arena_activity_emergency_contacts where user_id = :u",
                "delete from arena_post_reminders where user_id = :u",
                // Needs & offers
                "delete from arena_need_completions where response_id in " + myResponses,
                "delete from arena_post_responses where user_id = :u",
                "update arena_need_completions set owner_note = null where response_id in (select r.id from arena_post_responses r where r.post_id in " + myPosts + ")",
                "update arena_need_details set answers_json = '{}', proof_url = null, preferred_time = null where post_id in " + myPosts,
                // Career (the profile row itself is deleted by CandidateProfileService; locations first)
                "delete from arena_career_locations where career_id in (select id from arena_career_profiles where user_id = :u)",
                "delete from arena_career_profiles where user_id = :u",
                // Applications: theirs, and everything written about them
                "update arena_applications set cover_note = null, include_ctc = false, show_outcome = false where id in " + myApplications,
                "delete from arena_application_answers where application_id in " + myApplications,
                "delete from arena_application_notes where application_id in " + myApplications,
                "delete from arena_application_events where application_id in " + myApplications,
                "update arena_application_evidence set candidate_evidence = null, assessment = null, assessment_note = null, assessed_by_user_id = null, assessed_at = null where application_id in " + myApplications,
                "update arena_interviews set strengths = null, concerns = null, feedback_must_haves_json = null where application_id in " + myApplications,
                // Applications: what they wrote about others as a recruiter
                "delete from arena_application_notes where author_user_id = :u",
                "update arena_application_evidence set assessment_note = null, assessed_by_user_id = null where assessed_by_user_id = :u",
                "update arena_application_events set message = null, actor_user_id = null where actor_user_id = :u",
                "delete from arena_saved_jobs where user_id = :u",
                // Projects
                "delete from arena_project_contributors where user_id = :u",
                "update arena_project_members set message = null where user_id = :u",
                "update arena_project_milestones set created_by_user_id = null where created_by_user_id = :u",
                // People app
                "delete from arena_connect_requests where candidate_user_id = :u",
                "update arena_connect_requests set sender_user_id = null where sender_user_id = :u",
                "delete from arena_notification_preferences where user_id = :u",
                "delete from arena_user_active_days where user_id = :u",
                "delete from arena_staff_launch_areas where user_id = :u",
                "delete from arena_notifications where user_id = :u",
                "update arena_moderation_items set evidence_json = '[]' where reporter_user_id = :u",
                // Company verification they submitted: the work email is theirs
                "update arena_business_verifications set work_email = 'erased@erased.invalid', code_hash = null where tenant_id in (select id from arena_enterprise_profiles where user_id = :u)",
                "update arena_business_verifications set verified_by_user_id = null where verified_by_user_id = :u",
                "update arena_business_verifications set reviewed_by_user_id = null where reviewed_by_user_id = :u"
        );
        for (String sql : statements) jdbc.update(sql, p);
    }

    private List<String> readList(String json) {
        try {
            return objectMapper.readValue(json == null ? "[]" : json, new TypeReference<List<String>>() { });
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return List.of();
        }
    }

    // JSON-friendly values: timestamps and dates as ISO text, UUIDs as text.
    private static Map<String, Object> plain(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        row.forEach((k, v) -> out.put(k, switch (v) {
            case null -> null;
            case java.sql.Timestamp t -> t.toInstant().toString();
            case java.time.OffsetDateTime o -> o.toInstant().toString();
            case java.sql.Date d -> d.toLocalDate().toString();
            case UUID id -> id.toString();
            default -> v;
        }));
        return out;
    }
}
