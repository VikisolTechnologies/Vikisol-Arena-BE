package com.vikisol.arena.hiring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.enterprise.entity.CompanySize;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import com.vikisol.arena.jobs.entity.EmploymentType;
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

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Jobs & applications G22-G26: must-haves, screening, evidence, private per-must-have assessment, funnel and HIRED. */
@AutoConfigureMockMvc
class HiringFlowTest extends EmbeddedPostgresAppTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired CandidateProfileRepository profiles;
    @Autowired EnterpriseProfileRepository enterprises;
    @Autowired JobPostingRepository postings;
    @Autowired jakarta.persistence.EntityManager em;
    @MockBean TokenDenylistService denylist;

    private User recruiter, otherRecruiter, asha, ravi;
    private JobPosting job;

    @BeforeEach
    void setUp() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        recruiter = user(Role.COMPANY_ADMIN);
        EnterpriseProfile greenleaf = enterprises.save(EnterpriseProfile.builder().user(recruiter).companyName("GreenLeaf Labs")
                .logoEmoji("G").industry(Industry.DESIGN).size(CompanySize.S_11_50).build());
        otherRecruiter = user(Role.COMPANY_ADMIN);
        enterprises.save(EnterpriseProfile.builder().user(otherRecruiter).companyName("MapMyLane").logoEmoji("M")
                .industry(Industry.DESIGN).size(CompanySize.S_11_50).build());
        asha = talent();
        ravi = talent();
        job = postings.save(JobPosting.builder().enterprise(greenleaf).title("Product designer").industry(Industry.DESIGN)
                .location("Hyderabad").remote(false).employmentType(EmploymentType.FULL_TIME).salaryMin(1).salaryMax(2)
                .description("Design things").build());
    }

    @Test
    void requirementsAndQuestionsAreTenantOwnedAndFreeOfProtectedAttributes() throws Exception {
        String req = "/enterprise/postings/" + job.getId() + "/requirements";
        call(recruiter, put(req), "{\"mustHaves\":[\"Male candidates only\"]}").andExpect(status().isBadRequest());
        call(recruiter, put(req), "{\"mustHaves\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\",\"g\",\"h\",\"i\",\"j\",\"k\"]}").andExpect(status().isBadRequest());
        call(otherRecruiter, put(req), "{\"mustHaves\":[\"Figma\"]}").andExpect(status().isForbidden());
        call(asha, put(req), "{\"mustHaves\":[\"Figma\"]}").andExpect(status().isForbidden());
        call(recruiter, put(req), "{\"mustHaves\":[\"3+ years of product design\",\"Figma\"],\"niceToHaves\":[\"Motion design\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustHaves.length()").value(2))
                .andExpect(jsonPath("$.data.niceToHaves[0].text").value("Motion design"));

        String scr = "/enterprise/postings/" + job.getId() + "/screening";
        call(recruiter, put(scr), "{\"questions\":[{\"text\":\"What is your age?\"}]}").andExpect(status().isBadRequest());
        call(recruiter, put(scr), "{\"questions\":[{\"text\":\"When could you start?\"}]}").andExpect(status().isOk());

        call(asha, get("/jobs/" + job.getId() + "/requirements"), null)
                .andExpect(jsonPath("$.data.screeningQuestions[0].text").value("When could you start?"));
        mvc.perform(get("/jobs/" + job.getId() + "/requirements")).andExpect(status().isUnauthorized());
    }

    @Test
    void evidenceAndThePrivateAssessmentFlow() throws Exception {
        JsonNode reqs = body(call(recruiter, put("/enterprise/postings/" + job.getId() + "/requirements"),
                "{\"mustHaves\":[\"3+ years of product design\",\"Figma\"]}")).path("data");
        String years = reqs.path("mustHaves").get(0).path("id").asText();
        String figma = reqs.path("mustHaves").get(1).path("id").asText();
        String question = body(call(recruiter, put("/enterprise/postings/" + job.getId() + "/screening"),
                "{\"questions\":[{\"text\":\"When could you start?\"}]}")).path("data").path("screeningQuestions").get(0).path("id").asText();

        String app = body(call(asha, post("/applications"), "{\"jobId\":\"" + job.getId() + "\"}")).path("data").path("id").asText();
        call(asha, get("/applications/" + app + "/screening"), null).andExpect(jsonPath("$.data.requiredUnanswered").value(1));
        call(ravi, get("/applications/" + app + "/screening"), null).andExpect(status().isForbidden());
        call(asha, put("/applications/" + app + "/screening"),
                "{\"answers\":[{\"questionId\":\"" + question + "\",\"answer\":\"In 30 days\"}],"
                        + "\"evidence\":[{\"requirementId\":\"" + years + "\",\"evidence\":\"5 years at two product studios\"}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requiredUnanswered").value(0))
                .andExpect(jsonPath("$.data.evidence[0].candidateEvidence").value("5 years at two product studios"));

        String evidence = "/enterprise/applicants/" + app + "/evidence";
        call(recruiter, get(evidence), null)
                .andExpect(jsonPath("$.data.checklist[0].source").value("candidate"))
                .andExpect(jsonPath("$.data.checklist[1].source").value("not_provided"))
                .andExpect(jsonPath("$.data.summary.mustHaves").value(2))
                .andExpect(jsonPath("$.data.summary.withEvidence").value(1))
                .andExpect(jsonPath("$.data.answers[0].answer").value("In 30 days"));
        call(otherRecruiter, get(evidence), null).andExpect(status().isForbidden());

        call(recruiter, put("/enterprise/applicants/" + app + "/requirements/" + years), "{\"assessment\":\"met\",\"note\":\"Strong portfolio\"}")
                .andExpect(jsonPath("$.data.summary.met").value(1))
                .andExpect(jsonPath("$.data.checklist[0].note").value("Strong portfolio"));
        call(recruiter, put("/enterprise/applicants/" + app + "/requirements/" + figma), "{\"assessment\":\"great\"}")
                .andExpect(status().isBadRequest());
        call(recruiter, put("/enterprise/applicants/" + app + "/requirements/" + figma), "{\"assessment\":\"unclear\"}")
                .andExpect(jsonPath("$.data.summary.met").value(1));

        // The candidate never sees the team's assessment or note.
        String mine = call(asha, get("/applications/" + app + "/screening"), null).andReturn().getResponse().getContentAsString();
        assertThat(mine).doesNotContain("Strong portfolio").doesNotContain("assessment");

        call(recruiter, get("/enterprise/postings/" + job.getId() + "/evidence"), null)
                .andExpect(jsonPath("$.data[0].summary.met").value(1));
        // Once a candidate has shown evidence, the must-haves are fixed.
        call(recruiter, put("/enterprise/postings/" + job.getId() + "/requirements"), "{\"mustHaves\":[\"Other\"]}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void funnelCountsStagesIncludingHired() throws Exception {
        String a = body(call(asha, post("/applications"), "{\"jobId\":\"" + job.getId() + "\"}")).path("data").path("id").asText();
        body(call(ravi, post("/applications"), "{\"jobId\":\"" + job.getId() + "\"}"));
        // ARCHITECT-REVIEW-BE-1 blocker #4: hired is reachable only via screening -> interview ->
        // offer -> the candidate's own accept, never a direct company-set stage.
        call(recruiter, put("/enterprise/applicants/" + a + "/stage"), "{\"stage\":\"screening\"}").andExpect(status().isOk());
        call(recruiter, put("/enterprise/applicants/" + a + "/stage"), "{\"stage\":\"interview\"}").andExpect(status().isOk());
        call(recruiter, put("/enterprise/applicants/" + a + "/stage"), "{\"stage\":\"offer\"}").andExpect(status().isOk());
        call(asha, post("/applications/" + a + "/offer/accept"), null).andExpect(jsonPath("$.data.stage").value("hired"));
        call(recruiter, get("/enterprise/postings/" + job.getId() + "/funnel"), null)
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.stages.applied").value(1))
                .andExpect(jsonPath("$.data.stages.hired").value(1))
                .andExpect(jsonPath("$.data.stages.offer").value(0));
        call(otherRecruiter, get("/enterprise/postings/" + job.getId() + "/funnel"), null).andExpect(status().isForbidden());
    }

    @Test
    void withdrawingStillWorksAfterAnsweringScreening() throws Exception {
        String question = body(call(recruiter, put("/enterprise/postings/" + job.getId() + "/screening"),
                "{\"questions\":[{\"text\":\"When could you start?\"}]}")).path("data").path("screeningQuestions").get(0).path("id").asText();
        String app = body(call(asha, post("/applications"), "{\"jobId\":\"" + job.getId() + "\"}")).path("data").path("id").asText();
        call(asha, put("/applications/" + app + "/screening"), "{\"answers\":[{\"questionId\":\"" + question + "\",\"answer\":\"Now\"}]}");
        call(asha, delete("/applications/" + app), null).andExpect(status().isOk());
        em.flush(); // the DELETE really reaches Postgres: the answers cascade with the application
    }

    private ResultActions call(User as, MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header("Authorization", "Bearer " + tokens.generateToken(as.getId(), as.getEmail(), as.getName(), as.getRole()));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private User talent() {
        User u = user(Role.TALENT);
        profiles.save(CandidateProfile.builder().user(u).name("Candidate").avatarEmoji("*").title("Designer")
                .industry(Industry.DESIGN).location("Hyderabad").remote(false).consent(new ConsentSettings(false, true)).build());
        return u;
    }

    private User user(Role role) {
        return users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Person")
                .role(role).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
    }
}
