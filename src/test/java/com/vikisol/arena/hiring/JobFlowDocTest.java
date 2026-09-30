package com.vikisol.arena.hiring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikisol.arena.applications.entity.Application;
import com.vikisol.arena.applications.entity.ApplicationStage;
import com.vikisol.arena.applications.repository.ApplicationRepository;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.enterprise.entity.CompanySize;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import com.vikisol.arena.interviews.entity.Interview;
import com.vikisol.arena.interviews.repository.InterviewRepository;
import com.vikisol.arena.jobs.entity.JobPosting;
import com.vikisol.arena.jobs.repository.JobPostingRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Jobs per ARENA-APP-FLOW §6/§8: FE-API-GAPS rows 20, 22, 28, 33, 41. */
@AutoConfigureMockMvc
class JobFlowDocTest extends EmbeddedPostgresAppTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired CandidateProfileRepository profiles;
    @Autowired EnterpriseProfileRepository enterprises;
    @Autowired JobPostingRepository postings;
    @Autowired ApplicationRepository applications;
    @Autowired InterviewRepository interviews;
    @MockBean TokenDenylistService denylist;

    private User recruiter, asha, outsider;
    private CandidateProfile ashaProfile;

    private static final String DRAFT = "{\"title\":\"Community program assistant\",\"industry\":\"engineering\",\"location\":\"Gachibowli\","
            + "\"employmentType\":\"Full Time\",\"salaryMin\":400000,\"salaryMax\":600000,\"skills\":[],\"description\":\"Run programs\","
            + "\"status\":\"draft\",\"workMode\":\"hybrid\",\"experienceLevel\":\"Entry level (0–2 years)\",\"deadline\":\"2099-12-31\","
            + "\"mustHaves\":[\"Good communication\"],\"niceToHaves\":[\"First aid certificate\"],"
            + "\"questions\":[{\"text\":\"Are you available on weekends?\",\"type\":\"yesno\"},"
            + "{\"text\":\"Years of experience\",\"type\":\"number\",\"required\":false},"
            + "{\"text\":\"Preferred shift\",\"type\":\"choice\",\"options\":[\"Morning\",\"Evening\"]}]}";

    @BeforeEach
    void setUp() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        recruiter = user(Role.COMPANY_ADMIN);
        enterprises.save(EnterpriseProfile.builder().user(recruiter).companyName("Acme").logoEmoji("A")
                .industry(Industry.ENGINEERING).size(CompanySize.S_11_50).build());
        asha = user(Role.TALENT);
        ashaProfile = profiles.save(CandidateProfile.builder().user(asha).name("Asha").avatarEmoji("*").title("Organiser")
                .industry(Industry.ENGINEERING).location("Hyderabad").remote(false).experienceYears(1)
                .consent(new ConsentSettings(false, true)).build());
        outsider = user(Role.COMPANY_ADMIN);
        enterprises.save(EnterpriseProfile.builder().user(outsider).companyName("Other").logoEmoji("O")
                .industry(Industry.ENGINEERING).size(CompanySize.S_11_50).build());
    }

    @Test
    void aDraftIsOnlyForTheTeamUntilPublished() throws Exception {
        String id = draft();
        call(asha, get("/jobs"), null).andExpect(jsonPath("$.data.content[*].id", not(hasItem(id))));
        call(asha, get("/jobs/" + id), null).andExpect(status().isNotFound());
        call(asha, get("/jobs/" + id + "/requirements"), null).andExpect(status().isNotFound());
        call(asha, post("/applications"), "{\"jobId\":\"" + id + "\"}").andExpect(status().isNotFound());
        call(recruiter, get("/jobs/" + id + "/requirements"), null)
                .andExpect(jsonPath("$.data.mustHaves[0].text").value("Good communication"))
                .andExpect(jsonPath("$.data.screeningQuestions[2].options[1]").value("Evening"));

        call(recruiter, patch("/enterprise/postings/" + id), "{\"title\":\"Program assistant\",\"workMode\":\"remote\",\"niceToHaves\":[]}")
                .andExpect(jsonPath("$.data.title").value("Program assistant"))
                .andExpect(jsonPath("$.data.remote").value(true))
                .andExpect(jsonPath("$.data.status").value("draft"));
        call(outsider, patch("/enterprise/postings/" + id), "{\"title\":\"Mine\"}").andExpect(status().isForbidden());
        call(recruiter, patch("/enterprise/postings/" + id), "{\"salaryMin\":900000}").andExpect(status().isBadRequest());

        call(recruiter, put("/enterprise/postings/" + id + "/status"), "{\"status\":\"open\"}").andExpect(status().isOk());
        call(asha, get("/jobs/" + id), null)
                .andExpect(jsonPath("$.data.workMode").value("remote"))
                .andExpect(jsonPath("$.data.experienceLevel").value("entry"))
                .andExpect(jsonPath("$.data.deadline").value("2099-12-31"))
                .andExpect(jsonPath("$.data.mustHaves[0]").value("Good communication"))
                .andExpect(jsonPath("$.data.niceToHaves.length()").value(0))
                .andExpect(jsonPath("$.data.companyVerified").value(false))
                .andExpect(jsonPath("$.data.saved").value(false));
    }

    @Test
    void typedQuestionsCheckTheAnswers() throws Exception {
        String id = draft();
        call(recruiter, put("/enterprise/postings/" + id + "/status"), "{\"status\":\"open\"}").andExpect(status().isOk());
        JsonNode questions = body(call(asha, get("/jobs/" + id + "/questions"), null)).path("data");
        String weekends = questions.get(0).path("id").asText();
        String years = questions.get(1).path("id").asText();
        String shift = questions.get(2).path("id").asText();
        call(asha, get("/jobs/" + id + "/questions"), null)
                .andExpect(jsonPath("$.data[0].type").value("yesno"))
                .andExpect(jsonPath("$.data[0].label").value("Are you available on weekends?"))
                .andExpect(jsonPath("$.data[2].options[0]").value("Morning"));

        call(asha, post("/applications"), apply(id, weekends, "maybe", years, "3", shift, "Morning")).andExpect(status().isBadRequest());
        call(asha, post("/applications"), apply(id, weekends, "yes", years, "three", shift, "Morning")).andExpect(status().isBadRequest());
        call(asha, post("/applications"), apply(id, weekends, "yes", years, "3", shift, "Night")).andExpect(status().isBadRequest());
        call(asha, post("/applications"), apply(id, weekends, "Yes", years, "3", shift, "evening")).andExpect(status().isOk());
        String applicationId = applications.findByCandidateIdAndJobPostingId(ashaProfile.getId(), UUID.fromString(id)).orElseThrow().getId().toString();
        call(asha, get("/applications/" + applicationId + "/screening"), null)
                .andExpect(jsonPath("$.data.answers[0].answer").value("yes"))
                .andExpect(jsonPath("$.data.answers[2].answer").value("Evening"));
        // Now people have applied, it can't go back to draft.
        call(recruiter, put("/enterprise/postings/" + id + "/status"), "{\"status\":\"draft\"}").andExpect(status().isBadRequest());
    }

    @Test
    void closedPausedAndPastDeadlineJobsTakeNoApplications() throws Exception {
        String id = draft();
        call(recruiter, put("/enterprise/postings/" + id + "/status"), "{\"status\":\"paused\"}").andExpect(status().isOk());
        call(asha, post("/applications"), "{\"jobId\":\"" + id + "\"}").andExpect(status().isBadRequest());
        call(recruiter, put("/enterprise/postings/" + id + "/status"), "{\"status\":\"open\"}").andExpect(status().isOk());
        JobPosting job = postings.findById(UUID.fromString(id)).orElseThrow();
        job.setDeadline(LocalDate.now().minusDays(2));
        postings.save(job);
        call(asha, post("/applications"), "{\"jobId\":\"" + id + "\"}").andExpect(status().isBadRequest());
        call(recruiter, patch("/enterprise/postings/" + id), "{\"deadline\":\"2001-01-01\"}").andExpect(status().isBadRequest());
    }

    @Test
    void savedJobsArePrivateBookmarks() throws Exception {
        String id = draft();
        call(asha, post("/jobs/" + id + "/save"), null).andExpect(status().isNotFound()); // a draft can't be saved
        call(recruiter, put("/enterprise/postings/" + id + "/status"), "{\"status\":\"open\"}").andExpect(status().isOk());
        call(asha, post("/jobs/" + id + "/save"), null).andExpect(status().isOk());
        call(asha, post("/jobs/" + id + "/save"), null).andExpect(status().isOk());
        call(asha, get("/jobs/saved"), null)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(id))
                .andExpect(jsonPath("$.data[0].saved").value(true));
        call(asha, get("/jobs/" + id), null).andExpect(jsonPath("$.data.saved").value(true));
        call(outsider, get("/jobs/saved"), null).andExpect(jsonPath("$.data.length()").value(0));
        call(asha, delete("/jobs/" + id + "/save"), null).andExpect(status().isOk());
        call(asha, get("/jobs/saved"), null).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/jobs").param("size", "50")).andExpect(jsonPath("$.data.content[0].saved").doesNotExist());
    }

    @Test
    void interviewFeedbackIsPerMustHaveWithNoScoreNeeded() throws Exception {
        String id = draft();
        call(recruiter, put("/enterprise/postings/" + id + "/status"), "{\"status\":\"open\"}").andExpect(status().isOk());
        Application application = applications.save(Application.builder().candidate(ashaProfile)
                .jobPosting(postings.findById(UUID.fromString(id)).orElseThrow()).stage(ApplicationStage.INTERVIEW).appliedAt(Instant.now()).build());
        Interview interview = interviews.save(Interview.builder().application(application).build());
        call(recruiter, post("/interviews/" + interview.getId() + "/feedback"),
                "{\"recommendation\":\"hold\",\"mustHaves\":[{\"item\":\"Good communication\",\"seen\":\"great\"}]}")
                .andExpect(status().isBadRequest());
        call(recruiter, post("/interviews/" + interview.getId() + "/feedback"),
                "{\"recommendation\":\"hold\",\"strengths\":\"Clear\",\"mustHaves\":[{\"item\":\"Good communication\",\"seen\":\"strong\",\"note\":\"Explained a past event well\"}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feedback.rating").doesNotExist())
                .andExpect(jsonPath("$.data.feedback.mustHaves[0].seen").value("strong"))
                .andExpect(jsonPath("$.data.feedback.mustHaves[0].note").value("Explained a past event well"));
    }

    private static String apply(String jobId, String q1, String a1, String q2, String a2, String q3, String a3) {
        return "{\"jobId\":\"" + jobId + "\",\"answers\":[{\"questionId\":\"" + q1 + "\",\"value\":\"" + a1 + "\"},"
                + "{\"questionId\":\"" + q2 + "\",\"value\":\"" + a2 + "\"},{\"questionId\":\"" + q3 + "\",\"value\":\"" + a3 + "\"}]}";
    }

    private String draft() throws Exception {
        return body(call(recruiter, post("/enterprise/postings"), DRAFT).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andExpect(jsonPath("$.data.workMode").value("hybrid"))).path("data").path("id").asText();
    }

    private ResultActions call(User as, MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header("Authorization", "Bearer " + tokens.generateToken(as.getId(), as.getEmail(), as.getName(), as.getRole()));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private User user(Role role) {
        return users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Person")
                .role(role).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
    }
}
