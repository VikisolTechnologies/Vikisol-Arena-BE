package com.vikisol.arena.career;

import com.vikisol.arena.applications.entity.Application;
import com.vikisol.arena.applications.repository.ApplicationRepository;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.career.entity.CareerEnums.CompensationVisibility;
import com.vikisol.arena.career.entity.CareerProfile;
import com.vikisol.arena.career.service.CompensationPolicy;
import com.vikisol.arena.enterprise.entity.CompanySize;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.entity.UnlockedCandidate;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import com.vikisol.arena.enterprise.repository.UnlockedCandidateRepository;
import com.vikisol.arena.follows.entity.Follow;
import com.vikisol.arena.follows.repository.FollowRepository;
import com.vikisol.arena.jobs.entity.EmploymentType;
import com.vikisol.arena.jobs.entity.JobPosting;
import com.vikisol.arena.jobs.repository.JobPostingRepository;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.entity.CandidateSkill;
import com.vikisol.arena.profile.entity.ConsentSettings;
import com.vikisol.arena.profile.entity.Industry;
import com.vikisol.arena.profile.entity.LocationConsent;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Career profile G18-G21: setup, privacy preview, publish, per-audience views; pay private by default everywhere. */
@AutoConfigureMockMvc
class CareerFlowTest extends EmbeddedPostgresAppTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired CandidateProfileRepository profiles;
    @Autowired EnterpriseProfileRepository enterprises;
    @Autowired UnlockedCandidateRepository unlocks;
    @Autowired JobPostingRepository postings;
    @Autowired ApplicationRepository applications;
    @Autowired FollowRepository follows;
    @MockBean TokenDenylistService denylist;

    private User asha, neighbor, friend, recruiter;
    private CandidateProfile ashaProfile;
    private EnterpriseProfile acme;

    @BeforeEach
    void setUp() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        asha = user(Role.TALENT);
        ashaProfile = profiles.save(CandidateProfile.builder().user(asha).name("Asha").avatarEmoji("*").title("Designer")
                .industry(Industry.DESIGN).location("Hyderabad").remote(false).experienceYears(5)
                .skills(new ArrayList<>(List.of(new CandidateSkill("Figma", true))))
                .consent(new ConsentSettings(false, true)).currentCtc(1_800_000).expectedCtc(2_400_000)
                .locationConsent(LocationConsent.CITY).homeCity("Hyderabad").approxLat(17.4).approxLng(78.4).build());
        neighbor = user(Role.TALENT);
        friend = user(Role.TALENT);
        follows.save(Follow.builder().followerUser(asha).followingUser(friend).build());
        follows.save(Follow.builder().followerUser(friend).followingUser(asha).build());
        recruiter = user(Role.COMPANY_ADMIN);
        acme = enterprises.save(EnterpriseProfile.builder().user(recruiter).companyName("GreenLeaf Labs").logoEmoji("G")
                .industry(Industry.DESIGN).size(CompanySize.S_11_50).build());
    }

    @Test
    void setupValidatesAndPayDefaultsToPrivate() throws Exception {
        call(asha, put("/career/me"), "{\"desiredRole\":\"Product designer\"}").andExpect(status().isBadRequest());
        call(asha, put("/career/me"), "{\"intent\":\"find_job\",\"workMode\":\"sometimes\"}").andExpect(status().isBadRequest());
        call(asha, put("/career/me"), "{\"intent\":\"find_job\",\"expectedMin\":30,\"expectedMax\":10}").andExpect(status().isBadRequest());
        setup("{\"intent\":\"find_job\",\"desiredRole\":\"Product designer\",\"experienceLevel\":\"senior\",\"workMode\":\"hybrid\","
                + "\"preferredLocations\":[\"Hyderabad\",\"hyderabad\",\"Remote\"],\"noticePeriod\":\"days_30\","
                + "\"expectedMin\":2000000,\"expectedMax\":2600000}")
                .andExpect(jsonPath("$.data.compensationVisibility").value("private"))
                .andExpect(jsonPath("$.data.preferredLocations.length()").value(2))
                .andExpect(jsonPath("$.data.published").value(false));
        call(recruiter, put("/career/me"), "{\"intent\":\"find_job\"}").andExpect(status().isForbidden());
    }

    @Test
    void nothingIsVisibleUntilPublished() throws Exception {
        setup("{\"intent\":\"find_job\",\"desiredRole\":\"Product designer\"}");
        call(neighbor, get("/career/" + asha.getId()), null).andExpect(status().isNotFound());
        call(recruiter, get("/career/" + asha.getId()), null).andExpect(status().isNotFound());
        mvc.perform(get("/career/" + asha.getId())).andExpect(status().isUnauthorized());
    }

    @Test
    void eachAudienceSeesItsOwnSliceAndThePreviewMatches() throws Exception {
        setup("{\"intent\":\"find_job\",\"desiredRole\":\"Product designer\",\"experienceLevel\":\"senior\",\"workMode\":\"hybrid\","
                + "\"preferredLocations\":[\"Hyderabad\"],\"noticePeriod\":\"days_30\",\"expectedMin\":2000000,\"expectedMax\":2600000}");
        call(asha, post("/career/me/publish"), "{\"openToWork\":true}").andExpect(jsonPath("$.data.published").value(true));

        call(neighbor, get("/career/" + asha.getId()), null)
                .andExpect(jsonPath("$.data.audience").value("neighbor"))
                .andExpect(jsonPath("$.data.desiredRole").value("Product designer"))
                .andExpect(jsonPath("$.data.openToWork").value(true))
                .andExpect(jsonPath("$.data.skills[0]").value("Figma"))
                .andExpect(jsonPath("$.data.experienceLevel").doesNotExist())
                .andExpect(jsonPath("$.data.expectedMin").doesNotExist());
        call(friend, get("/career/" + asha.getId()), null)
                .andExpect(jsonPath("$.data.audience").value("connection"))
                .andExpect(jsonPath("$.data.workMode").value("hybrid"))
                .andExpect(jsonPath("$.data.preferredLocations").doesNotExist())
                .andExpect(jsonPath("$.data.expectedMin").doesNotExist());
        call(recruiter, get("/career/" + asha.getId()), null)
                .andExpect(jsonPath("$.data.audience").value("employer"))
                .andExpect(jsonPath("$.data.noticePeriod").value("days_30"))
                .andExpect(jsonPath("$.data.preferredLocations[0]").value("Hyderabad"))
                .andExpect(jsonPath("$.data.expectedMin").doesNotExist());

        call(asha, get("/career/me/preview"), null)
                .andExpect(jsonPath("$.data.compensationShownTo").value("nobody"))
                .andExpect(jsonPath("$.data.employers.expectedMin").doesNotExist())
                .andExpect(jsonPath("$.data.neighbors.experienceLevel").doesNotExist());

        // Opting in shows pay to employers - and the preview says so.
        setup("{\"compensationVisibility\":\"employers\"}");
        call(recruiter, get("/career/" + asha.getId()), null).andExpect(jsonPath("$.data.expectedMin").value(2000000));
        call(neighbor, get("/career/" + asha.getId()), null).andExpect(jsonPath("$.data.expectedMin").doesNotExist());
        call(asha, get("/career/me/preview"), null)
                .andExpect(jsonPath("$.data.compensationShownTo").value("employers"))
                .andExpect(jsonPath("$.data.employers.expectedMax").value(2600000));

        call(asha, post("/career/me/unpublish"), null).andExpect(jsonPath("$.data.published").value(false));
        call(neighbor, get("/career/" + asha.getId()), null).andExpect(status().isNotFound());
    }

    @Test
    void exploringQuietlyCantBePublished() throws Exception {
        setup("{\"intent\":\"find_job\"}");
        call(asha, post("/career/me/publish"), null).andExpect(status().isOk());
        setup("{\"intent\":\"explore_quietly\"}").andExpect(jsonPath("$.data.published").value(false));
        call(asha, post("/career/me/publish"), null).andExpect(status().isBadRequest());
    }

    @Test
    void talentSearchHidesPayAndHomeLocationUnlessTheCandidateSharesPay() throws Exception {
        unlocks.save(UnlockedCandidate.builder().enterprise(acme).candidate(ashaProfile).build());
        String detail = "/enterprise/talent/" + ashaProfile.getId();
        call(recruiter, get(detail), null)
                .andExpect(jsonPath("$.data.fullAccess").value(true))
                .andExpect(jsonPath("$.data.currentCtc").doesNotExist())
                .andExpect(jsonPath("$.data.expectedCtc").doesNotExist())
                .andExpect(jsonPath("$.data.homeCity").doesNotExist());
        setup("{\"intent\":\"find_job\",\"compensationVisibility\":\"employers\"}");
        call(recruiter, get(detail), null).andExpect(jsonPath("$.data.expectedCtc").value(2400000));
    }

    @Test
    void applicantListFollowsTheSameRule() throws Exception {
        JobPosting job = postings.save(JobPosting.builder().enterprise(acme).title("Product designer").industry(Industry.DESIGN)
                .location("Hyderabad").remote(false).employmentType(EmploymentType.FULL_TIME).salaryMin(1).salaryMax(2)
                .description("Design things").build());
        applications.save(Application.builder().candidate(ashaProfile).jobPosting(job).appliedAt(Instant.now()).build());
        String list = "/enterprise/postings/" + job.getId() + "/applicants";
        call(recruiter, get(list), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].candidate.fullAccess").value(true))
                .andExpect(jsonPath("$.data.content[0].candidate.currentCtc").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].candidate.approxLat").doesNotExist());
        setup("{\"intent\":\"find_job\",\"compensationVisibility\":\"on_application\"}");
        call(recruiter, get(list), null).andExpect(jsonPath("$.data.content[0].candidate.currentCtc").value(1800000));
    }

    @Test
    void compensationPolicy() {
        CareerProfile onApply = CareerProfile.builder().compensationVisibility(CompensationVisibility.ON_APPLICATION).build();
        CareerProfile employers = CareerProfile.builder().compensationVisibility(CompensationVisibility.EMPLOYERS).build();
        assertThat(CompensationPolicy.employerMaySee(null, true, true)).isFalse();
        assertThat(CompensationPolicy.employerMaySee(onApply, false, true)).isFalse();
        assertThat(CompensationPolicy.employerMaySee(onApply, true, false)).isTrue();
        assertThat(CompensationPolicy.employerMaySee(employers, false, true)).isTrue();
        assertThat(CompensationPolicy.employerMaySee(employers, false, false)).isFalse();
    }

    private ResultActions setup(String body) throws Exception {
        return call(asha, put("/career/me"), body).andExpect(status().isOk());
    }

    private ResultActions call(User as, MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header("Authorization", "Bearer " + tokens.generateToken(as.getId(), as.getEmail(), as.getName(), as.getRole()));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }

    private User user(Role role) {
        return users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Person")
                .role(role).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
    }
}
