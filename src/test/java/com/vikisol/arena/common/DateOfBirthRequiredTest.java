package com.vikisol.arena.common;

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
import com.vikisol.arena.messaging.service.ConversationService;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// B11 item 17: a phone or Google sign-up has no date of birth at all until onboarding's age gate
// runs. Every write action (post, join, message, apply, connect) must refuse such an account with
// "Add your date of birth to continue" - reads stay allowed either way.
@AutoConfigureMockMvc
class DateOfBirthRequiredTest extends EmbeddedPostgresAppTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired CandidateProfileRepository profiles;
    @Autowired EnterpriseProfileRepository enterprises;
    @Autowired JobPostingRepository postings;
    @Autowired ConversationService conversationService;
    @MockBean TokenDenylistService denylist;

    private User noDob, withDob, host;

    @BeforeEach
    void setUp() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        noDob = users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("No Dob")
                .role(Role.TALENT).build()); // no dateOfBirth
        withDob = user("With Dob");
        host = user("Host");
    }

    @Test
    void creatingAPostRequiresADateOfBirth() throws Exception {
        call(noDob, post("/posts"), "{\"intentType\":\"update\",\"body\":\"Hello\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Add your date of birth to continue"));
        call(withDob, post("/posts"), "{\"intentType\":\"update\",\"body\":\"Hello\"}").andExpect(status().isOk());
    }

    @Test
    void joiningAPostRequiresADateOfBirth() throws Exception {
        profile(host);
        String postId = body(call(host, post("/posts"),
                "{\"intentType\":\"activity\",\"body\":\"Join me\",\"capacity\":5,"
                        + "\"startsAt\":\"" + java.time.Instant.now().plus(3, java.time.temporal.ChronoUnit.DAYS) + "\","
                        + "\"activity\":{\"category\":\"fitness\",\"subtype\":\"yoga\"}}")).path("data").path("id").asText();
        call(noDob, post("/posts/" + postId + "/joins"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Add your date of birth to continue"));
        call(withDob, post("/posts/" + postId + "/joins"), null).andExpect(status().isOk());
    }

    @Test
    void sendingAMessageRequiresADateOfBirth() throws Exception {
        String conversationId = conversationService.getOrCreate(noDob.getId(), host.getId(), "test").id();
        call(noDob, post("/messages/conversations/" + conversationId + "/messages"), "{\"content\":\"hi\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Add your date of birth to continue"));
        String conversationId2 = conversationService.getOrCreate(withDob.getId(), host.getId(), "test").id();
        call(withDob, post("/messages/conversations/" + conversationId2 + "/messages"), "{\"content\":\"hi\"}").andExpect(status().isOk());
    }

    @Test
    void applyingToAJobRequiresADateOfBirth() throws Exception {
        profile(noDob);
        profile(withDob);
        User recruiter = user(Role.COMPANY_ADMIN, "Recruiter");
        EnterpriseProfile company = enterprises.save(EnterpriseProfile.builder().user(recruiter).companyName("Acme Co").logoEmoji("A")
                .industry(Industry.DESIGN).size(CompanySize.S_11_50).build());
        JobPosting job = postings.save(JobPosting.builder().enterprise(company).title("Designer").industry(Industry.DESIGN)
                .location("Hyderabad").remote(false).employmentType(EmploymentType.FULL_TIME).salaryMin(1).salaryMax(2)
                .description("Design things").build());
        call(noDob, post("/applications"), "{\"jobId\":\"" + job.getId() + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Add your date of birth to continue"));
        call(withDob, post("/applications"), "{\"jobId\":\"" + job.getId() + "\"}").andExpect(status().isOk());
    }

    @Test
    void sendingAConnectRequestRequiresADateOfBirthOnTheSender() throws Exception {
        CandidateProfile candidate = profile(withDob);
        User noDobRecruiter = User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("No Dob Recruiter")
                .role(Role.COMPANY_ADMIN).build();
        users.save(noDobRecruiter);
        enterprises.save(EnterpriseProfile.builder().user(noDobRecruiter).companyName("NoDob Co").logoEmoji("N")
                .industry(Industry.DESIGN).size(CompanySize.S_11_50).build());
        call(noDobRecruiter, post("/enterprise/talent/" + candidate.getId() + "/connect"), "{\"note\":\"Hi\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Add your date of birth to continue"));
    }

    private CandidateProfile profile(User u) {
        return profiles.save(CandidateProfile.builder().user(u).name(u.getName()).avatarEmoji("*").title("Designer")
                .industry(Industry.DESIGN).location("Hyderabad").remote(false).consent(new ConsentSettings(false, true)).build());
    }

    private com.fasterxml.jackson.databind.JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private ResultActions call(User as, MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header("Authorization", "Bearer " + tokens.generateToken(as.getId(), as.getEmail(), as.getName(), as.getRole()));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }

    private User user(String name) {
        return user(Role.TALENT, name);
    }

    private User user(Role role, String name) {
        return users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name(name)
                .role(role).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
    }
}
