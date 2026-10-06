package com.vikisol.arena.privacy;

import com.vikisol.arena.applications.entity.Application;
import com.vikisol.arena.applications.repository.ApplicationRepository;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.enterprise.entity.CompanySize;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import com.vikisol.arena.interviews.entity.Interview;
import com.vikisol.arena.interviews.repository.InterviewRepository;
import com.vikisol.arena.jobs.entity.EmploymentType;
import com.vikisol.arena.jobs.entity.JobPosting;
import com.vikisol.arena.jobs.entity.PostingStatus;
import com.vikisol.arena.jobs.repository.JobPostingRepository;
import com.vikisol.arena.platform.entity.FeatureFlag;
import com.vikisol.arena.platform.repository.FeatureFlagRepository;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.entity.ConsentSettings;
import com.vikisol.arena.profile.entity.Industry;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import com.vikisol.arena.schema.EmbeddedPostgresAppTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architect decision 30 Sep 2026: candidate data is deleted 12 months after a role closes, behind
 * candidate_retention_enabled (off), with a dry run that only counts.
 */
class CandidateRetentionTest extends EmbeddedPostgresAppTest {

    @Autowired CandidateRetentionService retention;
    @Autowired UserRepository users;
    @Autowired EnterpriseProfileRepository enterprises;
    @Autowired CandidateProfileRepository profiles;
    @Autowired JobPostingRepository postings;
    @Autowired ApplicationRepository applications;
    @Autowired InterviewRepository interviews;
    @Autowired FeatureFlagRepository flags;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    private EnterpriseProfile company;
    private User recruiter;

    @BeforeEach
    void setUp() {
        recruiter = user(Role.COMPANY_ADMIN);
        company = enterprises.save(EnterpriseProfile.builder().user(recruiter).companyName("Acme").logoEmoji("A")
                .industry(Industry.ENGINEERING).size(CompanySize.S_11_50).build());
    }

    @Test
    void closingAPostingStampsWhenAndReopeningClearsIt() {
        JobPosting job = posting("Tester");
        assertThat(job.getClosedAt()).isNull();
        job.setStatus(PostingStatus.CLOSED);
        assertThat(job.getClosedAt()).isNotNull();
        Instant first = job.getClosedAt();
        job.setStatus(PostingStatus.CLOSED);
        assertThat(job.getClosedAt()).isEqualTo(first); // closing again doesn't restart the clock
        job.setStatus(PostingStatus.OPEN);
        assertThat(job.getClosedAt()).isNull();
    }

    @Test
    void withTheFlagOffARunOnlyCountsAndDeletesNothing() {
        UUID old = applicationWithEverything(closedAgo(posting("Old role"), Duration.ofDays(400)));
        applicationWithEverything(closedAgo(posting("Recent role"), Duration.ofDays(300)));
        applicationWithEverything(posting("Open role"));

        CandidateRetentionService.Report report = retention.run();
        assertThat(report.enabled()).isFalse();
        assertThat(report.deleted()).isFalse();
        assertThat(report.postings()).isEqualTo(1);
        assertThat(report.applications()).isEqualTo(1);
        assertThat(report.screeningAnswers()).isEqualTo(1);
        assertThat(report.evidence()).isEqualTo(1);
        assertThat(report.notes()).isEqualTo(1);
        assertThat(report.events()).isEqualTo(1);
        assertThat(report.interviews()).isEqualTo(1);
        assertThat(report.interviewSlots()).isEqualTo(1);
        assertThat(count("arena_applications")).isEqualTo(3);
        assertThat(applications.existsById(old)).isTrue();
        // The admin's on-demand report shows the same counts (its cutoff is its own "now").
        assertThat(retention.report()).usingRecursiveComparison().ignoringFields("cutoff").isEqualTo(report);
    }

    @Test
    void withTheFlagOnOnlyApplicationsOnRolesClosedOverAYearAgoGo() {
        JobPosting oldRole = closedAgo(posting("Old role"), Duration.ofDays(400));
        UUID old = applicationWithEverything(oldRole);
        UUID recent = applicationWithEverything(closedAgo(posting("Recent role"), Duration.ofDays(300)));
        UUID open = applicationWithEverything(posting("Open role"));
        flags.save(FeatureFlag.builder().key(CandidateRetentionService.FLAG).label("Candidate retention").enabled(true).build());

        CandidateRetentionService.Report report = retention.run();
        assertThat(report.deleted()).isTrue();
        assertThat(report.applications()).isEqualTo(1);
        em.clear();

        assertThat(applications.existsById(old)).isFalse();
        assertThat(applications.existsById(recent)).isTrue();
        assertThat(applications.existsById(open)).isTrue();
        // Everything hanging off the deleted application went with it; the others kept theirs.
        assertThat(count("arena_application_answers")).isEqualTo(2);
        assertThat(count("arena_application_evidence")).isEqualTo(2);
        assertThat(count("arena_application_notes")).isEqualTo(2);
        assertThat(count("arena_application_events")).isEqualTo(2);
        assertThat(count("arena_interviews")).isEqualTo(2);
        assertThat(count("arena_interview_slots")).isEqualTo(2);
        // The posting is the company's and stays.
        assertThat(postings.existsById(oldRole.getId())).isTrue();

        assertThat(retention.run().applications()).isZero();
    }

    private JobPosting posting(String title) {
        return postings.save(JobPosting.builder().enterprise(company).title(title).industry(Industry.ENGINEERING)
                .location("Hyderabad").remote(false).employmentType(EmploymentType.FULL_TIME).salaryMin(1).salaryMax(2)
                .description("Work").build());
    }

    private JobPosting closedAgo(JobPosting job, Duration ago) {
        job.setStatus(PostingStatus.CLOSED);
        postings.saveAndFlush(job);
        jdbc.update("update arena_job_postings set closed_at = ? where id = ?",
                java.sql.Timestamp.from(Instant.now().minus(ago)), job.getId());
        return job;
    }

    // One application with a row in every table the job deletes from.
    private UUID applicationWithEverything(JobPosting job) {
        User candidate = user(Role.TALENT);
        CandidateProfile profile = profiles.save(CandidateProfile.builder().user(candidate).name("Candidate").avatarEmoji("*")
                .title("Engineer").industry(Industry.ENGINEERING).location("Hyderabad").remote(false).experienceYears(1)
                .consent(new ConsentSettings(false, true)).build());
        Application app = applications.save(Application.builder().candidate(profile).jobPosting(job).appliedAt(Instant.now()).build());
        Interview interview = interviews.save(Interview.builder().application(app).build());
        em.flush();
        UUID question = UUID.randomUUID(), requirement = UUID.randomUUID();
        jdbc.update("insert into arena_job_screening_questions (id, created_at, updated_at, posting_id, position, text) values (?, now(), now(), ?, 0, 'Can you start soon?')",
                question, job.getId());
        jdbc.update("insert into arena_job_requirements (id, created_at, updated_at, posting_id, kind, position, text) values (?, now(), now(), ?, 'MUST', 0, 'Java')",
                requirement, job.getId());
        jdbc.update("insert into arena_application_answers (id, created_at, updated_at, application_id, question_id, answer) values (?, now(), now(), ?, ?, 'yes')",
                UUID.randomUUID(), app.getId(), question);
        jdbc.update("insert into arena_application_evidence (id, created_at, updated_at, application_id, requirement_id, candidate_evidence) values (?, now(), now(), ?, ?, 'Five years')",
                UUID.randomUUID(), app.getId(), requirement);
        jdbc.update("insert into arena_application_notes (id, created_at, updated_at, application_id, author_user_id, text) values (?, now(), now(), ?, ?, 'Strong')",
                UUID.randomUUID(), app.getId(), recruiter.getId());
        jdbc.update("insert into arena_application_events (id, created_at, updated_at, application_id, type, stage) values (?, now(), now(), ?, 'STAGE', 'SCREENING')",
                UUID.randomUUID(), app.getId());
        jdbc.update("insert into arena_interview_slots (id, created_at, updated_at, duration_minutes, start, interview_id) values (?, now(), now(), 30, now(), ?)",
                UUID.randomUUID(), interview.getId());
        return app.getId();
    }

    private long count(String table) {
        em.flush();
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    private User user(Role role) {
        return users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Person")
                .role(role).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
    }
}
