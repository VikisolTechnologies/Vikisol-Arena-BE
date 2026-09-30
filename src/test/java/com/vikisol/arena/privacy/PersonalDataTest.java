package com.vikisol.arena.privacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.repository.PostRepository;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.entity.ConsentSettings;
import com.vikisol.arena.profile.entity.Industry;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import com.vikisol.arena.schema.EmbeddedPostgresAppTest;
import com.vikisol.arena.security.jwt.JwtTokenProvider;
import com.vikisol.arena.security.jwt.TokenDenylistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Architect item 4 (a legal requirement): data export and erasure cover the new tables - host
 * answers, feedback, recruiter notes and assessments, dispute text, offer messages and the rest.
 * Export includes the person's own entries; erasure deletes them.
 */
@AutoConfigureMockMvc
class PersonalDataTest extends EmbeddedPostgresAppTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired CandidateProfileRepository profiles;
    @Autowired EnterpriseProfileRepository enterprises;
    @Autowired ApplicationRepository applications;
    @Autowired InterviewRepository interviews;
    @Autowired PostRepository posts;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.vikisol.arena.enterprise.repository.MembershipRepository memberships;
    @MockBean TokenDenylistService denylist;
    // Revoking sessions lives in Redis, which the test sandbox doesn't run.
    @MockBean com.vikisol.arena.security.jwt.RefreshTokenService refreshTokens;

    private User host, asha, ravi, recruiter;
    private CandidateProfile ashaProfile;

    // Tables added on this branch that hold no person's own entries, and why.
    private static final Map<String, String> NOT_PERSONAL = Map.of(
            "arena_candidate_intents", "cleared by CandidateProfileService's erasure with the profile",
            "arena_candidate_interests", "cleared by CandidateProfileService's erasure with the profile",
            "arena_candidate_availability", "cleared by CandidateProfileService's erasure with the profile",
            "arena_activity_details", "part of the activity post (content, like the post itself)",
            "arena_job_requirements", "the company's job content",
            "arena_job_screening_questions", "the company's job content",
            "arena_project_roles", "part of the project post",
            "arena_project_details", "part of the project post (category, cover, outcome)",
            "arena_business_verifications", "the company's record; the submitter's work email is handled in erase()",
            "arena_industries", "the staff-managed industry list (V44): reference data, no person's entries");

    @BeforeEach
    void setUp() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        host = talent("Host");
        asha = talent("Asha");
        ashaProfile = profiles.findByUserId(asha.getId()).orElseThrow();
        ravi = talent("Ravi");
        recruiter = user(Role.COMPANY_ADMIN, "Recruiter");
        enterprises.save(EnterpriseProfile.builder().user(recruiter).companyName("Acme").logoEmoji("A")
                .industry(Industry.DESIGN).size(CompanySize.S_11_50).plan(com.vikisol.arena.enterprise.entity.Plan.ENTERPRISE).build());
    }

    @Test
    void everyTableAddedForTheNewAppIsCoveredOrExplainedWhyNot() throws IOException {
        String service = Files.readString(Path.of("src/main/java/com/vikisol/arena/privacy/PersonalDataService.java"));
        Pattern create = Pattern.compile("CREATE TABLE IF NOT EXISTS public\\.(arena_\\w+)");
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.list(Path.of("src/main/resources/db/migration"))) {
            for (Path f : files.filter(p -> version(p) >= 22).toList()) {
                Matcher m = create.matcher(Files.readString(f));
                while (m.find()) {
                    String table = m.group(1);
                    if (!NOT_PERSONAL.containsKey(table) && !service.contains(table + " ")) missing.add(table + " (" + f.getFileName() + ")");
                }
            }
        }
        assertThat(missing).as("new tables that neither PersonalDataService covers nor NOT_PERSONAL explains").isEmpty();
    }

    @Test
    void exportIncludesThePersonsOwnEntriesAndErasureDeletesThem() throws Exception {
        seedEverything();

        // Export: the talent's own entries, section by section.
        JsonNode arena = body(call(asha, get("/profile/me/export"), null).andExpect(status().isOk())).path("data").path("arena");
        for (String section : List.of("activityAnswers", "activityWaitlist", "joinNotes", "attendance", "activityFeedbackGiven",
                "activityFeedbackReceived", "emergencyContacts", "reminders", "needDetails", "needResponses", "needCompletionNotes",
                "careerProfile", "applications", "screeningAnswers", "mustHaveEvidence", "applicationTimeline", "savedJobs",
                "projectMemberships", "projectMilestonesAdded", "projectContributions", "connectRequestsReceived",
                "notificationPreferences", "reportEvidence")) {
            assertThat(arena.path(section)).as(section).isNotEmpty();
        }
        assertThat(arena.path("attendance").get(0).path("dispute_reason").asText()).isEqualTo("I was there, ask the others");
        assertThat(arena.path("applications").get(0).path("cover_note").asText()).isEqualTo("I love organising");
        assertThat(arena.path("needResponses").get(0).path("message").asText()).isEqualTo("I can lend one");
        assertThat(arena.path("activityAnswers").get(0).path("answer").asText()).isEqualTo("Twice, up Ananthagiri");
        // The recruiter's and the host's own entries, through the any-account export.
        JsonNode recruiterData = body(call(recruiter, get("/account/export"), null).andExpect(status().isOk())).path("data").path("arena");
        for (String section : List.of("recruiterNotesWritten", "assessmentsWritten", "stageMessagesWritten", "connectRequestsSent")) {
            assertThat(recruiterData.path(section)).as(section).isNotEmpty();
        }
        assertThat(recruiterData.path("recruiterNotesWritten").get(0).path("text").asText()).isEqualTo("Strong on logistics");
        JsonNode hostData = body(call(host, get("/account/export"), null)).path("data").path("arena");
        assertThat(hostData.path("activityQuestionsAsked")).isNotEmpty();
        assertThat(hostData.path("hostDecisionNotesWritten")).isNotEmpty();

        // Erasure: none of the talent's entries (or the private records about them) are left.
        UUID a = asha.getId();
        String apps = "(select id from arena_applications where candidate_id = '" + ashaProfile.getId() + "')";
        call(asha, delete("/profile/me"), null).andExpect(status().isOk());
        Map<String, String> none = new LinkedHashMap<>();
        none.put("host answers", "select count(*) from arena_activity_answers where user_id = '" + a + "'");
        none.put("waitlist", "select count(*) from arena_activity_waitlist where user_id = '" + a + "'");
        none.put("dispute text / attendance", "select count(*) from arena_activity_attendance t join arena_post_joins j on j.id = t.join_id where j.user_id = '" + a + "'");
        none.put("join notes", "select count(*) from arena_post_joins where user_id = '" + a + "' and (note is not null or decision_note is not null)");
        none.put("feedback", "select count(*) from arena_activity_feedback where from_user_id = '" + a + "' or to_user_id = '" + a + "'");
        none.put("emergency contacts", "select count(*) from arena_activity_emergency_contacts where user_id = '" + a + "'");
        none.put("reminders", "select count(*) from arena_post_reminders where user_id = '" + a + "'");
        none.put("offer messages", "select count(*) from arena_post_responses where user_id = '" + a + "'");
        none.put("completion notes", "select count(*) from arena_need_completions c join arena_post_responses r on r.id = c.response_id join arena_posts p on p.id = r.post_id where p.author_user_id = '" + a + "' and c.owner_note is not null");
        none.put("need answers", "select count(*) from arena_need_details d join arena_posts p on p.id = d.post_id where p.author_user_id = '" + a + "' and d.answers_json <> '{}'");
        none.put("career", "select count(*) from arena_career_profiles where user_id = '" + a + "'");
        none.put("cover note", "select count(*) from arena_applications where id in " + apps + " and cover_note is not null");
        none.put("screening answers", "select count(*) from arena_application_answers where application_id in " + apps);
        none.put("recruiter notes about them", "select count(*) from arena_application_notes where application_id in " + apps);
        none.put("stage messages to them", "select count(*) from arena_application_events where application_id in " + apps);
        none.put("evidence and assessments", "select count(*) from arena_application_evidence where application_id in " + apps
                + " and (candidate_evidence is not null or assessment is not null or assessment_note is not null)");
        none.put("interview feedback", "select count(*) from arena_interviews where application_id in " + apps
                + " and (strengths is not null or feedback_must_haves_json is not null)");
        none.put("saved jobs", "select count(*) from arena_saved_jobs where user_id = '" + a + "'");
        none.put("project contributions", "select count(*) from arena_project_contributors where user_id = '" + a + "'");
        none.put("project messages", "select count(*) from arena_project_members where user_id = '" + a + "' and message is not null");
        none.put("milestones by them", "select count(*) from arena_project_milestones where created_by_user_id = '" + a + "'");
        none.put("connect requests", "select count(*) from arena_connect_requests where candidate_user_id = '" + a + "'");
        none.put("notification preferences", "select count(*) from arena_notification_preferences where user_id = '" + a + "'");
        none.put("notifications", "select count(*) from arena_notifications where user_id = '" + a + "'");
        none.put("report evidence", "select count(*) from arena_moderation_items where reporter_user_id = '" + a + "' and evidence_json <> '[]'");
        none.forEach((what, sql) -> assertThat(jdbc.queryForObject(sql, Long.class)).as(what).isZero());

        // A recruiter's own notes, assessments and messages go when their account is erased.
        call(admin(), delete("/admin/users/" + recruiter.getId()), null).andExpect(status().isBadRequest()); // a company admin isn't erased this way
        User teammate = recruiterOnTeam();
        UUID t = teammate.getId();
        call(admin(), delete("/admin/users/" + t), null).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from arena_application_notes where author_user_id = '" + t + "'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from arena_application_evidence where assessed_by_user_id = '" + t + "'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from arena_application_events where actor_user_id = '" + t + "'", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from arena_connect_requests where sender_user_id = '" + t + "'", Long.class)).isZero();
    }

    // A second recruiter on the same company, with a note, an assessment, a stage message and a
    // connect request of their own.
    private User recruiterOnTeam() throws Exception {
        User teammate = user(Role.RECRUITER, "Teammate");
        var company = enterprises.findAll().stream().filter(e -> e.getUser().getId().equals(recruiter.getId())).findFirst().orElseThrow();
        memberships.save(com.vikisol.arena.enterprise.entity.Membership.builder().user(teammate).tenant(company).joinedAt(Instant.now()).build());
        CandidateProfile raviProfile = profiles.findByUserId(ravi.getId()).orElseThrow();
        String job = body(call(teammate, post("/enterprise/postings"), jobBody("Coordinator 2"))).path("data").path("id").asText();
        call(ravi, post("/applications"), "{\"jobId\":\"" + job + "\"}").andExpect(status().isOk());
        String applicationId = applications.findByCandidateIdAndJobPostingId(raviProfile.getId(), UUID.fromString(job)).orElseThrow().getId().toString();
        call(teammate, post("/enterprise/applicants/" + applicationId + "/notes"), "{\"text\":\"Teammate's note\"}").andExpect(status().isOk());
        String requirement = body(call(teammate, get("/jobs/" + job + "/requirements"), null)).path("data").path("mustHaves").get(0).path("id").asText();
        call(teammate, put("/enterprise/applicants/" + applicationId + "/requirements/" + requirement), "{\"assessment\":\"met\",\"note\":\"Clear\"}")
                .andExpect(status().isOk());
        call(teammate, put("/enterprise/applicants/" + applicationId + "/stage"), "{\"stage\":\"screening\",\"message\":\"Thanks!\"}").andExpect(status().isOk());
        call(teammate, post("/enterprise/talent/" + profiles.findByUserId(host.getId()).orElseThrow().getId() + "/connect"), "{\"note\":\"Hello\"}")
                .andExpect(status().isOk());
        return teammate;
    }

    private void seedEverything() throws Exception {
        // Activities: a trek with a host question, joined with an answer, a note and an emergency contact.
        String trek = id(call(host, post("/posts"), "{\"intentType\":\"activity\",\"body\":\"Ananthagiri trek\",\"visibility\":\"approval\",\"startsAt\":\""
                + Instant.now().plus(2, ChronoUnit.DAYS) + "\",\"activity\":{\"category\":\"outdoors\",\"subtype\":\"trekking\"},"
                + "\"hostQuestions\":[\"Have you trekked before?\"]}"));
        String question = body(mvc.perform(get("/activities/" + trek))).path("data").path("questions").get(0).path("id").asText();
        String join = id(call(asha, post("/activities/" + trek + "/join"), "{\"answers\":[{\"questionId\":\"" + question
                + "\",\"answer\":\"Twice, up Ananthagiri\"}],\"note\":\"Can't wait\",\"emergencyContact\":{\"name\":\"Mum\",\"phone\":\"+91 98480 00000\"}}"));
        call(host, put("/posts/" + trek + "/joins/" + join + "/approve"), "{\"note\":\"Meet at the gate\"}").andExpect(status().isOk());
        call(asha, post("/posts/" + trek + "/reminder"), "{\"minutesBefore\":60}").andExpect(status().isOk());
        Post p = posts.findById(UUID.fromString(trek)).orElseThrow();
        p.setStartsAt(Instant.now().minus(3, ChronoUnit.HOURS));
        posts.save(p);
        call(host, put("/posts/" + trek + "/joins/" + join + "/outcome"), "{\"outcome\":\"no_show\"}").andExpect(status().isOk());
        call(asha, post("/activities/" + trek + "/attendance/dispute"), "{\"reason\":\"I was there, ask the others\"}").andExpect(status().isOk());
        call(asha, post("/activities/" + trek + "/feedback"), "{\"joinAgain\":true,\"note\":\"Great route\"}").andExpect(status().isOk());
        call(host, post("/activities/" + trek + "/feedback"), "{\"toUserId\":\"" + asha.getId() + "\",\"joinAgain\":false,\"note\":\"Came late\"}")
                .andExpect(status().isOk());
        // A full activity she waits for.
        String chess = id(call(host, post("/posts"), "{\"intentType\":\"activity\",\"body\":\"Chess\",\"capacity\":1,\"startsAt\":\""
                + Instant.now().plus(3, ChronoUnit.DAYS) + "\",\"activity\":{\"category\":\"games\",\"subtype\":\"chess\",\"waitlist\":true}}"));
        call(ravi, post("/posts/" + chess + "/joins"), null).andExpect(status().isOk());
        call(asha, post("/activities/" + chess + "/waitlist"), "{}").andExpect(status().isOk());

        // Needs: her need with an intake, an offer on it she completes with a note, and her offer on someone else's need.
        String need = id(call(asha, post("/posts"), "{\"intentType\":\"ask\",\"body\":\"Need a ladder\",\"need\":{\"category\":\"borrow\",\"answers\":{\"item\":\"Ladder\"}}}"));
        String response = id(call(ravi, post("/needs/" + need + "/responses"), "{\"message\":\"Mine is tall\"}"));
        call(asha, put("/needs/" + need + "/responses/" + response + "/accept"), null).andExpect(status().isOk());
        call(asha, post("/needs/" + need + "/responses/" + response + "/confirm"), "{\"note\":\"Thank you Ravi\"}").andExpect(status().isOk());
        String hostNeed = id(call(host, post("/posts"), "{\"intentType\":\"ask\",\"body\":\"Need a drill\"}"));
        call(asha, post("/needs/" + hostNeed + "/responses"), "{\"message\":\"I can lend one\"}").andExpect(status().isOk());

        // Career.
        call(asha, put("/career/me"), "{\"intent\":\"find_job\",\"currentCompany\":\"GreenLeaf\",\"currentCtc\":{\"fixed\":900000}}").andExpect(status().isOk());

        // A job: applied with an answer, a cover note and her pay; evidence; a recruiter note, assessment and message.
        String job = id(call(recruiter, post("/enterprise/postings"), jobBody("Coordinator")));
        JsonNode reqs = body(call(asha, get("/jobs/" + job + "/requirements"), null)).path("data");
        call(asha, post("/applications"), "{\"jobId\":\"" + job + "\",\"answers\":[{\"questionId\":\"" + reqs.path("screeningQuestions").get(0).path("id").asText()
                + "\",\"value\":\"yes\"}],\"coverNote\":\"I love organising\",\"includeCtc\":true}").andExpect(status().isOk());
        Application app = applications.findByCandidateIdAndJobPostingId(ashaProfile.getId(), UUID.fromString(job)).orElseThrow();
        String mustHave = reqs.path("mustHaves").get(0).path("id").asText();
        call(asha, put("/applications/" + app.getId() + "/screening"), "{\"evidence\":[{\"requirementId\":\"" + mustHave + "\",\"evidence\":\"Ran 3 camps\"}]}")
                .andExpect(status().isOk());
        call(recruiter, post("/enterprise/applicants/" + app.getId() + "/notes"), "{\"text\":\"Strong on logistics\"}").andExpect(status().isOk());
        call(recruiter, put("/enterprise/applicants/" + app.getId() + "/requirements/" + mustHave), "{\"assessment\":\"met\",\"note\":\"Clear examples\"}")
                .andExpect(status().isOk());
        call(recruiter, put("/enterprise/applicants/" + app.getId() + "/stage"), "{\"stage\":\"interview\",\"message\":\"Let's talk\"}").andExpect(status().isOk());
        Interview interview = interviews.save(Interview.builder().application(applications.findById(app.getId()).orElseThrow()).build());
        call(recruiter, post("/interviews/" + interview.getId() + "/feedback"),
                "{\"recommendation\":\"hold\",\"strengths\":\"Calm\",\"mustHaves\":[{\"item\":\"Organising\",\"seen\":\"strong\"}]}").andExpect(status().isOk());
        call(asha, post("/jobs/" + job + "/save"), null).andExpect(status().isOk());

        // A project: applied with a note, added a milestone, named a contributor.
        JsonNode project = body(call(host, post("/projects"), "{\"title\":\"Lake map\",\"goal\":\"Map it\",\"category\":\"environment\",\"roles\":[{\"title\":\"Mapper\"}]}")).path("data");
        String projectId = project.path("postId").asText();
        String projectJoin = id(call(asha, post("/projects/" + projectId + "/applications"), "{\"roleId\":\"" + project.path("roles").get(0).path("id").asText() + "\",\"note\":\"I map\"}"));
        call(host, put("/posts/" + projectId + "/joins/" + projectJoin + "/approve"), null).andExpect(status().isOk());
        call(asha, post("/projects/" + projectId + "/milestones"), "{\"title\":\"East shore\"}").andExpect(status().isOk());
        call(host, post("/projects/" + projectId + "/complete"), "{\"outcome\":\"Mapped\",\"contributorIds\":[\"" + asha.getId() + "\"]}").andExpect(status().isOk());

        // People app: a connect request to her, her notification settings, a report with evidence.
        call(recruiter, post("/enterprise/talent/" + ashaProfile.getId() + "/connect"), "{\"note\":\"We're hiring\"}").andExpect(status().isOk());
        call(asha, put("/notifications/preferences"), "{\"job\":false}").andExpect(status().isOk());
        String evidence = body(call(asha, multipart("/reports/evidence").file(new MockMultipartFile("file", "shot.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0})), null)).path("data").path("url").asText();
        call(asha, post("/posts/" + hostNeed + "/report"), "{\"reason\":\"Rude\",\"evidenceUrls\":[\"" + evidence + "\"]}").andExpect(status().isOk());
    }

    private static String jobBody(String title) {
        return "{\"title\":\"" + title + "\",\"industry\":\"design\",\"location\":\"Hyderabad\",\"employmentType\":\"Full Time\","
                + "\"salaryMin\":1,\"salaryMax\":2,\"skills\":[],\"description\":\"Coordinate\",\"mustHaves\":[\"Organising\"],"
                + "\"questions\":[{\"text\":\"Weekends ok?\",\"type\":\"yesno\"}]}";
    }

    private static int version(Path p) {
        Matcher m = Pattern.compile("^V(\\d+)__").matcher(p.getFileName().toString());
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private String id(ResultActions r) throws Exception {
        return body(r.andExpect(status().isOk())).path("data").path("id").asText();
    }

    private User admin() {
        return users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Staff")
                .role(Role.PLATFORM_ADMIN).totpEnabled(true).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
    }

    private User talent(String name) {
        User u = user(Role.TALENT, name);
        profiles.save(CandidateProfile.builder().user(u).name(name).avatarEmoji("*").title("Organiser").industry(Industry.DESIGN)
                .location("Hyderabad").remote(false).experienceYears(3).consent(new ConsentSettings(false, true)).build());
        return u;
    }

    private ResultActions call(User as, MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header("Authorization", "Bearer " + tokens.generateToken(as.getId(), as.getEmail(), as.getName(), as.getRole()));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private User user(Role role, String name) {
        return users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name(name)
                .role(role).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
    }
}
