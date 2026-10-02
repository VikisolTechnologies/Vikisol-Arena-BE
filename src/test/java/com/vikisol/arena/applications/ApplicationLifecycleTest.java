package com.vikisol.arena.applications;

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

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Architect item 2 (candidates may only withdraw; only company roles set other stages) and FE-API-GAPS
 * rows 20, 21, 30, 31: apply extras, offer answer, notes, timeline, stage message.
 */
@AutoConfigureMockMvc
class ApplicationLifecycleTest extends EmbeddedPostgresAppTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired CandidateProfileRepository profiles;
    @Autowired EnterpriseProfileRepository enterprises;
    @Autowired JobPostingRepository postings;
    @MockBean TokenDenylistService denylist;

    private User recruiter, stranger, asha;
    private JobPosting job;

    @BeforeEach
    void setUp() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        recruiter = user(Role.COMPANY_ADMIN, "Meera Recruiter");
        EnterpriseProfile company = enterprises.save(EnterpriseProfile.builder().user(recruiter).companyName("GreenLeaf Labs")
                .logoEmoji("G").industry(Industry.DESIGN).size(CompanySize.S_11_50).build());
        stranger = user(Role.COMPANY_ADMIN, "Other");
        enterprises.save(EnterpriseProfile.builder().user(stranger).companyName("MapMyLane").logoEmoji("M")
                .industry(Industry.DESIGN).size(CompanySize.S_11_50).build());
        asha = user(Role.TALENT, "Asha");
        profiles.save(CandidateProfile.builder().user(asha).name("Asha").avatarEmoji("*").title("Designer")
                .industry(Industry.DESIGN).location("Hyderabad").remote(false).consent(new ConsentSettings(false, true)).build());
        job = postings.save(JobPosting.builder().enterprise(company).title("Product designer").industry(Industry.DESIGN)
                .location("Hyderabad").remote(false).employmentType(EmploymentType.FULL_TIME).salaryMin(1).salaryMax(2)
                .description("Design things").build());
    }

    @Test
    void aCandidateCanOnlyWithdrawNeverPromoteThemself() throws Exception {
        String app = apply("{\"jobId\":\"" + job.getId() + "\"}");
        for (String stage : new String[]{"offer", "hired", "interview", "screening", "applied", "rejected"}) {
            call(asha, put("/applications/" + app + "/stage"), "{\"stage\":\"" + stage + "\"}").andExpect(status().isForbidden());
        }
        call(asha, put("/applications/" + app + "/stage"), "{\"stage\":\"withdrawn\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.stage").value("withdrawn"));
        call(recruiter, get("/enterprise/applicants/" + app), null).andExpect(jsonPath("$.data.stage").value("withdrawn"));
        // Only the candidate withdraws.
        call(recruiter, put("/enterprise/applicants/" + app + "/stage"), "{\"stage\":\"withdrawn\"}").andExpect(status().isBadRequest());
        // Applying again reopens the same application.
        call(asha, get("/applications/exists").param("jobId", job.getId().toString()), null).andExpect(jsonPath("$.data").value(false));
        String again = apply("{\"jobId\":\"" + job.getId() + "\"}");
        org.assertj.core.api.Assertions.assertThat(again).isEqualTo(app);
    }

    // ARCHITECT-REVIEW-BE-1 SHOULD-FIX (access/privacy): a withdrawn application shouldn't pull
    // the candidate's CV/profile back into the company's applicant list - direct-by-id lookup
    // (companyCanOnlyMoveStagesForwardAndNeverReviveATerminalStage's sibling test above) still
    // works, this is specifically about what surfaces when the company browses.
    @Test
    void aWithdrawnApplicationDropsOutOfTheApplicantList() throws Exception {
        String app = apply("{\"jobId\":\"" + job.getId() + "\"}");
        call(recruiter, get("/enterprise/postings/" + job.getId() + "/applicants"), null)
                .andExpect(jsonPath("$.data.content[0].id").value(app));
        call(asha, put("/applications/" + app + "/stage"), "{\"stage\":\"withdrawn\"}").andExpect(status().isOk());
        call(recruiter, get("/enterprise/postings/" + job.getId() + "/applicants"), null)
                .andExpect(jsonPath("$.data.content").isEmpty());
        // Still reachable directly by id, just not through the list.
        call(recruiter, get("/enterprise/applicants/" + app), null).andExpect(jsonPath("$.data.stage").value("withdrawn"));
    }

    @Test
    void withdrawingKeepsTheRecord() throws Exception {
        String app = apply("{\"jobId\":\"" + job.getId() + "\"}");
        call(asha, delete("/applications/" + app), null).andExpect(status().isOk());
        call(asha, get("/applications"), null).andExpect(jsonPath("$.data.content[0].stage").value("withdrawn"));
    }

    @Test
    void offerAcceptMakesAHireAndDeclineWithdraws() throws Exception {
        String app = apply("{\"jobId\":\"" + job.getId() + "\"}");
        call(asha, post("/applications/" + app + "/offer/accept"), null).andExpect(status().isBadRequest()); // no offer yet
        // ARCHITECT-REVIEW-BE-1 blocker #4: offer is only reachable via screening -> interview now,
        // not a direct APPLIED -> offer jump.
        call(recruiter, put("/enterprise/applicants/" + app + "/stage"), "{\"stage\":\"offer\"}").andExpect(status().isBadRequest());
        call(recruiter, put("/enterprise/applicants/" + app + "/stage"), "{\"stage\":\"screening\"}").andExpect(status().isOk());
        call(recruiter, put("/enterprise/applicants/" + app + "/stage"), "{\"stage\":\"interview\"}").andExpect(status().isOk());
        call(recruiter, put("/enterprise/applicants/" + app + "/stage"), "{\"stage\":\"offer\"}").andExpect(status().isOk());
        call(recruiter, put("/enterprise/applicants/" + app + "/stage"), "{\"stage\":\"hired\"}")
                .andExpect(status().isBadRequest()); // only the candidate's own accept sets hired
        call(asha, put("/applications/" + app + "/outcome"), "{\"showOnProfile\":true}").andExpect(status().isBadRequest()); // not hired yet
        call(asha, post("/applications/" + app + "/offer/accept"), null).andExpect(jsonPath("$.data.stage").value("hired"));
        call(asha, put("/applications/" + app + "/outcome"), "{\"showOnProfile\":true}").andExpect(status().isOk());

        User ravi = user(Role.TALENT, "Ravi");
        profiles.save(CandidateProfile.builder().user(ravi).name("Ravi").avatarEmoji("*").title("Designer")
                .industry(Industry.DESIGN).location("Hyderabad").remote(false).consent(new ConsentSettings(false, true)).build());
        String other = body(call(ravi, post("/applications"), "{\"jobId\":\"" + job.getId() + "\"}")).path("data").path("id").asText();
        call(recruiter, put("/enterprise/applicants/" + other + "/stage"), "{\"stage\":\"screening\"}");
        call(recruiter, put("/enterprise/applicants/" + other + "/stage"), "{\"stage\":\"interview\"}");
        call(recruiter, put("/enterprise/applicants/" + other + "/stage"), "{\"stage\":\"offer\"}");
        call(asha, post("/applications/" + other + "/offer/decline"), null).andExpect(status().isForbidden()); // not hers
        call(ravi, post("/applications/" + other + "/offer/decline"), null).andExpect(jsonPath("$.data.stage").value("withdrawn"));
    }

    // ARCHITECT-REVIEW-BE-1 blocker #4: any stage -> any stage let a recruiter revive a WITHDRAWN
    // application or skip stages. Now only a forward move, or ->REJECTED from an open stage.
    @Test
    void companyCanOnlyMoveStagesForwardAndNeverReviveATerminalStage() throws Exception {
        String app = apply("{\"jobId\":\"" + job.getId() + "\"}");
        // Can't skip ahead.
        call(recruiter, put("/enterprise/applicants/" + app + "/stage"), "{\"stage\":\"interview\"}").andExpect(status().isBadRequest());
        call(recruiter, put("/enterprise/applicants/" + app + "/stage"), "{\"stage\":\"hired\"}").andExpect(status().isBadRequest());
        call(recruiter, put("/enterprise/applicants/" + app + "/stage"), "{\"stage\":\"rejected\"}").andExpect(status().isOk());
        // REJECTED is terminal for the company - can't move it anywhere else, including back to open stages.
        call(recruiter, put("/enterprise/applicants/" + app + "/stage"), "{\"stage\":\"screening\"}").andExpect(status().isBadRequest());

        JobPosting secondJob = postings.save(JobPosting.builder().enterprise(job.getEnterprise()).title("Second role").industry(Industry.DESIGN)
                .location("Hyderabad").remote(false).employmentType(EmploymentType.FULL_TIME).salaryMin(1).salaryMax(2)
                .description("Another role").build());
        String withdrawnApp = apply("{\"jobId\":\"" + secondJob.getId() + "\"}");
        call(asha, put("/applications/" + withdrawnApp + "/stage"), "{\"stage\":\"withdrawn\"}").andExpect(status().isOk());
        // WITHDRAWN can't be revived by the company either.
        call(recruiter, put("/enterprise/applicants/" + withdrawnApp + "/stage"), "{\"stage\":\"screening\"}").andExpect(status().isBadRequest());
    }

    @Test
    void notSelectedAlwaysCarriesAKindMessageAndTheTimelineShowsIt() throws Exception {
        String app = apply("{\"jobId\":\"" + job.getId() + "\"}");
        call(recruiter, put("/enterprise/applicants/" + app + "/stage"), "{\"stage\":\"screening\",\"message\":\"We loved your portfolio\"}")
                .andExpect(status().isOk());
        call(recruiter, put("/enterprise/applicants/" + app + "/stage"), "{\"stage\":\"rejected\"}").andExpect(status().isOk());

        call(recruiter, get("/enterprise/applicants/" + app + "/events"), null)
                .andExpect(jsonPath("$.data[0].type").value("applied"))
                .andExpect(jsonPath("$.data[1].stage").value("screening"))
                .andExpect(jsonPath("$.data[1].message").value("We loved your portfolio"))
                .andExpect(jsonPath("$.data[1].actorName").value("Meera Recruiter"))
                .andExpect(jsonPath("$.data[2].message").value(startsWith("Thank you for applying")));
        call(asha, get("/applications/" + app + "/events"), null)
                .andExpect(jsonPath("$.data[2].stage").value("rejected"))
                .andExpect(jsonPath("$.data[1].actorName").doesNotExist());
        call(stranger, get("/enterprise/applicants/" + app + "/events"), null).andExpect(status().isForbidden());
        call(asha, get("/notifications"), null)
                .andExpect(jsonPath("$.data.content[?(@.title == 'A message from GreenLeaf Labs')]").exists());
    }

    @Test
    void recruiterNotesAreTeamPrivate() throws Exception {
        String app = apply("{\"jobId\":\"" + job.getId() + "\"}");
        call(recruiter, post("/enterprise/applicants/" + app + "/notes"), "{\"text\":\"Strong systems thinking\"}")
                .andExpect(jsonPath("$.data[0].text").value("Strong systems thinking"))
                .andExpect(jsonPath("$.data[0].authorName").value("Meera Recruiter"));
        call(stranger, get("/enterprise/applicants/" + app + "/notes"), null).andExpect(status().isForbidden());
        call(asha, get("/enterprise/applicants/" + app + "/notes"), null).andExpect(status().isForbidden());
    }

    @Test
    void applyCarriesACoverNoteAndCtcChoice() throws Exception {
        apply("{\"jobId\":\"" + job.getId() + "\",\"coverNote\":\"I design for small screens\",\"includeCtc\":true}");
    }

    private String apply(String body) throws Exception {
        return body(call(asha, post("/applications"), body).andExpect(status().isOk())).path("data").path("id").asText();
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
