package com.vikisol.arena.career;

import com.vikisol.arena.applications.entity.Application;
import com.vikisol.arena.applications.repository.ApplicationRepository;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Career per ARENA-APP-FLOW §6 and §8: FE-API-GAPS rows 19 and 32, and talent search open-only. */
@AutoConfigureMockMvc
class CareerFieldsTest extends EmbeddedPostgresAppTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired CandidateProfileRepository profiles;
    @Autowired EnterpriseProfileRepository enterprises;
    @Autowired JobPostingRepository postings;
    @Autowired ApplicationRepository applications;
    @MockBean TokenDenylistService denylist;

    private User asha, neighbor, recruiter, otherRecruiter;
    private CandidateProfile ashaProfile;
    private EnterpriseProfile acme;

    private static final String FULL = "{\"intent\":\"find_job\",\"currentCompany\":\"GreenLeaf Labs\",\"status\":\"notice\","
            + "\"noticePeriod\":\"30 days\",\"lastWorkingDay\":\"2026-10-31\",\"experienceMonths\":62,\"roleFamily\":\"sap\","
            + "\"skills\":[{\"name\":\"ABAP\",\"proficiency\":\"strong\",\"years\":4}],\"sapModules\":[\"fi\",\"MM\"],"
            + "\"certifications\":[\"SAP Certified Associate\"],\"currentCtc\":{\"fixed\":1800000,\"variable\":200000},"
            + "\"expectedCtc\":{\"min\":2400000,\"max\":3000000},\"negotiable\":true,\"desiredRoles\":[\"SAP FI consultant\"],"
            + "\"workModes\":[\"hybrid\",\"remote\"],\"relocate\":false,\"shift\":\"day\",\"companySizes\":[\"51-200\"],"
            + "\"links\":[\"https://github.com/asha\"],\"education\":{\"degree\":\"bachelor's\",\"institution\":\"JNTU\",\"year\":2019},"
            + "\"languages\":[\"Telugu\",\"English\"],\"visibility\":{\"languages\":\"only_me\"}}";

    @BeforeEach
    void setUp() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        asha = user(Role.TALENT);
        ashaProfile = profiles.save(CandidateProfile.builder().user(asha).name("Asha").avatarEmoji("*").title("Zebra SAP consultant")
                .industry(Industry.ENGINEERING).location("Hyderabad").remote(false).experienceYears(5)
                .consent(new ConsentSettings(false, true)).build());
        neighbor = user(Role.TALENT);
        recruiter = user(Role.COMPANY_ADMIN);
        acme = enterprises.save(EnterpriseProfile.builder().user(recruiter).companyName("Acme").logoEmoji("A")
                .industry(Industry.ENGINEERING).size(CompanySize.S_11_50).build());
        otherRecruiter = user(Role.COMPANY_ADMIN);
        enterprises.save(EnterpriseProfile.builder().user(otherRecruiter).companyName("Other").logoEmoji("O")
                .industry(Industry.ENGINEERING).size(CompanySize.S_11_50).build());
    }

    @Test
    void theOwnerSeesEveryFieldWithItsVisibility() throws Exception {
        call(asha, put("/career/me"), FULL).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.noticePeriod").value("days_30"))
                .andExpect(jsonPath("$.data.desiredRole").value("SAP FI consultant"))
                .andExpect(jsonPath("$.data.workMode").value("any"))
                .andExpect(jsonPath("$.data.details.roleFamily").value("SAP"))
                .andExpect(jsonPath("$.data.details.sapModules[0]").value("FI"))
                .andExpect(jsonPath("$.data.details.skills[0].proficiency").value("strong"))
                .andExpect(jsonPath("$.data.details.companySizes[0]").value("51–200"))
                .andExpect(jsonPath("$.data.details.education.degree").value("Bachelor's"))
                .andExpect(jsonPath("$.data.details.currentCtc.fixed").value(1800000))
                .andExpect(jsonPath("$.data.visibility.currentCompany").value("employers_i_apply"))
                .andExpect(jsonPath("$.data.visibility.currentCtc").value("only_me"))
                .andExpect(jsonPath("$.data.visibility.languages").value("only_me"))
                .andExpect(jsonPath("$.data.visibility.skills").value("public"));
        call(asha, put("/career/me"), "{\"visibility\":{\"currentCtc\":\"public\"}}").andExpect(status().isBadRequest());
        call(asha, put("/career/me"), "{\"visibility\":{\"age\":\"public\"}}").andExpect(status().isBadRequest());
        call(asha, put("/career/me"), "{\"links\":[\"ftp://x\"]}").andExpect(status().isBadRequest());
        call(asha, put("/career/me"), "{\"skills\":[{\"name\":\"Java\",\"proficiency\":\"guru\"}]}").andExpect(status().isBadRequest());
        call(asha, put("/career/me"), "{\"roleFamily\":\"Astronaut\"}").andExpect(status().isBadRequest());
    }

    @Test
    void eachFieldFollowsItsVisibility() throws Exception {
        call(asha, put("/career/me"), FULL).andExpect(status().isOk());
        call(asha, post("/career/me/publish"), "{\"openToWork\":true}").andExpect(status().isOk());

        // A neighbour: public extras only; never the company, languages (only me) or pay.
        call(neighbor, get("/career/" + asha.getId()), null)
                .andExpect(jsonPath("$.data.details.roleFamily").value("SAP"))
                .andExpect(jsonPath("$.data.details.skills[0].name").value("ABAP"))
                .andExpect(jsonPath("$.data.details.currentCompany").doesNotExist())
                .andExpect(jsonPath("$.data.details.languages").doesNotExist())
                .andExpect(jsonPath("$.data.details.currentCtc").doesNotExist());
        // An employer she hasn't applied to: no company, no last day.
        call(recruiter, get("/career/" + asha.getId()), null)
                .andExpect(jsonPath("$.data.noticePeriod").value("days_30"))
                .andExpect(jsonPath("$.data.details.currentCompany").doesNotExist())
                .andExpect(jsonPath("$.data.details.lastWorkingDay").doesNotExist());

        JobPosting job = postings.save(JobPosting.builder().enterprise(acme).title("SAP FI").industry(Industry.ENGINEERING)
                .location("Hyderabad").remote(false).employmentType(EmploymentType.FULL_TIME).salaryMin(1).salaryMax(2)
                .description("FI").build());
        applications.save(Application.builder().candidate(ashaProfile).jobPosting(job).appliedAt(Instant.now()).build());
        // The employer she applied to: company and last day too, still no pay.
        call(recruiter, get("/career/" + asha.getId()), null)
                .andExpect(jsonPath("$.data.details.currentCompany").value("GreenLeaf Labs"))
                .andExpect(jsonPath("$.data.details.lastWorkingDay").value("2026-10-31"))
                .andExpect(jsonPath("$.data.details.expectedCtc").doesNotExist());
        call(otherRecruiter, get("/career/" + asha.getId()), null)
                .andExpect(jsonPath("$.data.details.currentCompany").doesNotExist());
        // Row 32: the applicant list carries the career block with the notice period.
        call(recruiter, get("/enterprise/postings/" + job.getId() + "/applicants"), null)
                .andExpect(jsonPath("$.data.content[0].career.noticePeriod").value("days_30"))
                .andExpect(jsonPath("$.data.content[0].career.details.currentCompany").value("GreenLeaf Labs"))
                .andExpect(jsonPath("$.data.content[0].career.details.languages").doesNotExist());

        // Hiding the notice period hides it from employers too.
        call(asha, put("/career/me"), "{\"visibility\":{\"noticePeriod\":\"only_me\"}}").andExpect(status().isOk());
        call(recruiter, get("/career/" + asha.getId()), null).andExpect(jsonPath("$.data.noticePeriod").doesNotExist());
    }

    @Test
    void talentSearchShowsOnlyPublishedCareerProfiles() throws Exception {
        String search = "/enterprise/talent/search";
        call(recruiter, get(search).param("text", "zebra sap"), null).andExpect(jsonPath("$.data.content.length()").value(0));
        call(asha, put("/career/me"), "{\"intent\":\"find_job\"}").andExpect(status().isOk());
        call(recruiter, get(search).param("text", "zebra sap"), null).andExpect(jsonPath("$.data.content.length()").value(0));
        call(asha, post("/career/me/publish"), null).andExpect(status().isOk());
        call(recruiter, get(search).param("text", "zebra sap"), null).andExpect(jsonPath("$.data.content.length()").value(1));
        call(asha, post("/career/me/unpublish"), null).andExpect(status().isOk());
        call(recruiter, get(search).param("text", "zebra sap"), null).andExpect(jsonPath("$.data.content.length()").value(0));
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
