package com.vikisol.arena.business;

import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.business.repository.BusinessVerificationRepository;
import com.vikisol.arena.enterprise.entity.CompanySize;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.entity.Membership;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import com.vikisol.arena.enterprise.repository.MembershipRepository;
import com.vikisol.arena.integration.provider.EmailMessage;
import com.vikisol.arena.integration.provider.EmailProvider;
import com.vikisol.arena.integration.provider.ProviderException;
import com.vikisol.arena.platform.entity.FeatureFlag;
import com.vikisol.arena.profile.entity.Industry;
import com.vikisol.arena.schema.EmbeddedPostgresAppTest;
import com.vikisol.arena.security.jwt.JwtTokenProvider;
import com.vikisol.arena.security.jwt.TokenDenylistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Business verification (G27) and the team role catalogue (G28). */
@AutoConfigureMockMvc
class BusinessVerificationTest extends EmbeddedPostgresAppTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired EnterpriseProfileRepository enterprises;
    @Autowired MembershipRepository memberships;
    @Autowired BusinessVerificationRepository verifications;
    @Autowired com.vikisol.arena.platform.repository.FeatureFlagRepository flags;
    @MockBean TokenDenylistService denylist;
    @MockBean EmailProvider email;

    private User admin, recruiter, talent;
    private EnterpriseProfile company;

    @BeforeEach
    void setUp() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        admin = user(Role.COMPANY_ADMIN);
        company = enterprises.save(EnterpriseProfile.builder().user(admin).companyName("GreenLeaf Labs").logoEmoji("G")
                .industry(Industry.DESIGN).size(CompanySize.S_11_50).build());
        recruiter = user(Role.RECRUITER);
        memberships.save(Membership.builder().user(recruiter).tenant(company).joinedAt(Instant.now()).build());
        talent = user(Role.TALENT);
    }

    private static final String SUBMIT = "{\"legalName\":\"GreenLeaf Labs Pvt Ltd\",\"website\":\"https://www.greenleaf.example\","
            + "\"workEmail\":\"asha@greenleaf.example\",\"submitterRole\":\"founder\"}";

    // Flow §8 B2 / §9: the code proves the domain; the badge comes only when an Arena admin approves.
    @Test
    void aConfirmedDomainThenAnAdminApprovalEarnsThePublicBadge() throws Exception {
        mvc.perform(get("/companies/" + company.getId() + "/verification"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.verified").value(false));

        call(admin, post("/enterprise/verification"), SUBMIT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.domain").value("greenleaf.example"))
                .andExpect(jsonPath("$.data.workEmail").value("a***@greenleaf.example"));
        String code = sentCode();

        String wrong = code.equals("123456") ? "654321" : "123456";
        call(admin, post("/enterprise/verification/confirm"), "{\"code\":\"" + wrong + "\"}").andExpect(status().isBadRequest());
        call(recruiter, post("/enterprise/verification/confirm"), "{\"code\":\"" + code + "\"}").andExpect(status().isForbidden());
        call(admin, post("/enterprise/verification/confirm"), "{\"code\":\"" + code + "\"}")
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andExpect(jsonPath("$.data.domainConfirmed").value(true));
        mvc.perform(get("/companies/" + company.getId() + "/verification")).andExpect(jsonPath("$.data.verified").value(false));

        // The Arena admin's queue: approve.
        String id = verifications.findByTenantId(company.getId()).orElseThrow().getId().toString();
        call(platformAdmin(), get("/admin/verifications"), null)
                .andExpect(jsonPath("$.data[0].id").value(id))
                .andExpect(jsonPath("$.data[0].workEmail").value("asha@greenleaf.example"));
        call(admin, put("/admin/verifications/" + id + "/approve"), null).andExpect(status().isForbidden());
        call(platformAdmin(), put("/admin/verifications/" + id + "/approve"), null).andExpect(jsonPath("$.data.status").value("verified"));

        call(recruiter, get("/enterprise/verification"), null).andExpect(jsonPath("$.data.status").value("verified"));
        call(recruiter, get("/enterprise/profile/me"), null).andExpect(jsonPath("$.data.verification").value("verified"));
        mvc.perform(get("/companies/" + company.getId() + "/verification"))
                .andExpect(jsonPath("$.data.verified").value(true))
                .andExpect(jsonPath("$.data.domain").value("greenleaf.example"));
        // The code is never stored in the clear.
        assertThat(verifications.findByTenantId(company.getId()).orElseThrow().getCodeHash()).isNull();
    }

    @Test
    void theWorkEmailMustBeAtTheWebsitesDomainAndNotAPersonalMailbox() throws Exception {
        call(admin, post("/enterprise/verification"), SUBMIT.replace("asha@greenleaf.example", "asha@gmail.com"))
                .andExpect(status().isBadRequest());
        call(admin, post("/enterprise/verification"), SUBMIT.replace("asha@greenleaf.example", "asha@other.example"))
                .andExpect(status().isBadRequest());
        call(admin, post("/enterprise/verification"), SUBMIT.replace("\"founder\"", "\"ceo\"")).andExpect(status().isBadRequest());
        // A subdomain mailbox is fine.
        call(admin, post("/enterprise/verification"), SUBMIT.replace("asha@greenleaf.example", "asha@hr.greenleaf.example"))
                .andExpect(status().isOk());
    }

    @Test
    void onlyTheCompanyAdminSubmitsAndWrongCodesRunOut() throws Exception {
        call(recruiter, post("/enterprise/verification"), SUBMIT).andExpect(status().isForbidden());
        call(talent, post("/enterprise/verification"), SUBMIT).andExpect(status().isForbidden());
        call(admin, post("/enterprise/verification"), SUBMIT).andExpect(status().isOk());
        String code = sentCode();
        String wrong = code.equals("123456") ? "654321" : "123456";
        for (int i = 0; i < 5; i++) {
            call(admin, post("/enterprise/verification/confirm"), "{\"code\":\"" + wrong + "\"}").andExpect(status().isBadRequest());
        }
        call(admin, post("/enterprise/verification/confirm"), "{\"code\":\"" + code + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Too many wrong codes. Ask for a new one."));
        // Asking again straight away is throttled.
        call(admin, post("/enterprise/verification"), SUBMIT).andExpect(status().isBadRequest());
    }

    @Test
    void aFailedEmailIsAnHonest503AndNeverVerifies() throws Exception {
        doThrow(new ProviderException(ProviderException.Kind.EMAIL)).when(email).sendEmail(any());
        call(admin, post("/enterprise/verification"), SUBMIT)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("We couldn't send the code right now. Please try again in a minute."));
        mvc.perform(get("/companies/" + company.getId() + "/verification")).andExpect(jsonPath("$.data.verified").value(false));
    }

    @Test
    void anAdminCanRejectWithAReasonAndOnlyAfterTheDomainIsConfirmed() throws Exception {
        call(recruiter, get("/enterprise/verification"), null).andExpect(jsonPath("$.data.status").value("none"));
        call(admin, post("/enterprise/verification"), SUBMIT.replace("}", ",\"gstin\":\"36aabcg1234h1z5\",\"hqCity\":\"Hyderabad\"}"))
                .andExpect(status().isOk());
        String id = verifications.findByTenantId(company.getId()).orElseThrow().getId().toString();
        call(platformAdmin(), get("/admin/verifications"), null).andExpect(jsonPath("$.data.length()").value(0)); // code not confirmed yet
        call(platformAdmin(), put("/admin/verifications/" + id + "/approve"), null).andExpect(status().isBadRequest());
        call(admin, post("/enterprise/verification/confirm"), "{\"code\":\"" + sentCode() + "\"}").andExpect(status().isOk());
        call(platformAdmin(), get("/admin/verifications"), null).andExpect(jsonPath("$.data[0].gstin").value("36AABCG1234H1Z5"));
        call(platformAdmin(), put("/admin/verifications/" + id + "/reject"), "{}").andExpect(status().isBadRequest());
        call(platformAdmin(), put("/admin/verifications/" + id + "/reject"), "{\"note\":\"The GSTIN is for a different company\"}")
                .andExpect(jsonPath("$.data.status").value("rejected"));
        call(recruiter, get("/enterprise/verification"), null)
                .andExpect(jsonPath("$.data.status").value("rejected"))
                .andExpect(jsonPath("$.data.reviewNote").value("The GSTIN is for a different company"));
        call(admin, get("/enterprise/profile/me"), null)
                .andExpect(jsonPath("$.data.verificationNote").value("The GSTIN is for a different company"))
                .andExpect(jsonPath("$.data.hqCity").value("Hyderabad"));
        mvc.perform(get("/companies/" + company.getId() + "/verification")).andExpect(jsonPath("$.data.verified").value(false));
        call(admin, post("/enterprise/verification"), SUBMIT.replace("}", ",\"gstin\":\"not-a-gstin\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void withTheFlagOnJobsStayDraftsUntilTheCompanyIsVerified() throws Exception {
        String job = "{\"title\":\"Designer\",\"industry\":\"design\",\"location\":\"Hyderabad\",\"employmentType\":\"Full Time\","
                + "\"salaryMin\":1,\"salaryMax\":2,\"skills\":[],\"description\":\"Design\"";
        // Flag off (the default): publishing works as before.
        String first = body(call(admin, post("/enterprise/postings"), job + "}").andExpect(status().isOk()));
        call(admin, put("/enterprise/postings/" + first + "/status"), "{\"status\":\"closed\"}").andExpect(status().isOk());
        flags.save(FeatureFlag.builder().key("company_verification_required").label("Verification before publishing").enabled(true).build());
        call(admin, post("/enterprise/postings"), job + "}").andExpect(status().isBadRequest());
        String draft = body(call(admin, post("/enterprise/postings"), job + ",\"status\":\"draft\"}").andExpect(status().isOk()));
        call(admin, put("/enterprise/postings/" + draft + "/status"), "{\"status\":\"open\"}").andExpect(status().isBadRequest());
        verifications.save(com.vikisol.arena.business.entity.BusinessVerification.builder().tenant(company).legalName("GreenLeaf")
                .website("https://greenleaf.example").domain("greenleaf.example").workEmail("asha@greenleaf.example")
                .submitterRole(com.vikisol.arena.business.entity.BusinessVerification.SubmitterRole.FOUNDER)
                .status(com.vikisol.arena.business.entity.BusinessVerification.Status.VERIFIED).verifiedAt(Instant.now()).build());
        call(admin, put("/enterprise/postings/" + draft + "/status"), "{\"status\":\"open\"}").andExpect(status().isOk());
    }

    @Test
    void theWorkspaceKeepsItsExtraDetails() throws Exception {
        String base = "{\"companyName\":\"GreenLeaf Labs\",\"logoEmoji\":\"G\",\"industry\":\"design\",\"size\":\"11-50\",\"hiringFor\":[]";
        call(admin, put("/enterprise/profile/me"), base + ",\"website\":\"https://greenleaf.example\",\"cin\":\"U72900TG2019PTC123456\",\"hqCity\":\"Hyderabad\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.website").value("https://greenleaf.example"))
                .andExpect(jsonPath("$.data.cin").value("U72900TG2019PTC123456"))
                .andExpect(jsonPath("$.data.verification").value("none"));
        call(admin, put("/enterprise/profile/me"), base + ",\"cin\":\"123\"}").andExpect(status().isBadRequest());
        call(admin, multipart("/enterprise/profile/me/logo").file(new org.springframework.mock.web.MockMultipartFile("file", "logo.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0})), null)
                .andExpect(jsonPath("$.data.logoUrl").value(org.hamcrest.Matchers.containsString("sig="))); // signed, so it loads
        call(recruiter, delete("/enterprise/profile/me/logo"), null).andExpect(status().isForbidden());
        call(admin, delete("/enterprise/profile/me/logo"), null).andExpect(jsonPath("$.data.logoUrl").doesNotExist());
    }

    @Test
    void teamRolesCatalogueIsForTheTeamOnly() throws Exception {
        call(recruiter, get("/enterprise/team/roles"), null)
                .andExpect(jsonPath("$.data[0].role").value("company_admin"))
                .andExpect(jsonPath("$.data[1].can[0]").value("post_and_manage_jobs"));
        call(talent, get("/enterprise/team/roles"), null).andExpect(status().isForbidden());
    }

    private String sentCode() {
        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(email, atLeastOnce()).sendEmail(sent.capture());
        Matcher m = Pattern.compile(">(\\d{6})<").matcher(sent.getValue().htmlBody());
        assertThat(m.find()).isTrue();
        assertThat(sent.getValue().to()).containsExactly("asha@greenleaf.example");
        return m.group(1);
    }

    private ResultActions call(User as, MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header("Authorization", "Bearer " + tokens.generateToken(as.getId(), as.getEmail(), as.getName(), as.getRole()));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }

    private User platformAdmin() {
        return users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Staff")
                .role(Role.PLATFORM_ADMIN).totpEnabled(true).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
    }

    private String body(ResultActions result) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.andReturn().getResponse().getContentAsString())
                .path("data").path("id").asText();
    }

    private User user(Role role) {
        return users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Person")
                .role(role).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
    }
}
