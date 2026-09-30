package com.vikisol.arena.business;

import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.business.entity.BusinessVerification;
import com.vikisol.arena.business.repository.BusinessVerificationRepository;
import com.vikisol.arena.enterprise.entity.CompanySize;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import com.vikisol.arena.profile.entity.Industry;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Architect decisions of 30 Sep 2026 (DECISIONS.md): verification grandfathering behind the
 * company_verification_required flag, display-only billing, and "owner" as the company admin.
 */
@AutoConfigureMockMvc
class LaunchDecisionsTest extends EmbeddedPostgresAppTest {

    private static final String JOB = "{\"title\":\"Designer\",\"industry\":\"design\",\"location\":\"Hyderabad\","
            + "\"employmentType\":\"Full Time\",\"salaryMin\":1,\"salaryMax\":2,\"skills\":[],\"description\":\"Design\"}";

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired EnterpriseProfileRepository enterprises;
    @Autowired BusinessVerificationRepository verifications;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager em;
    @MockBean TokenDenylistService denylist;

    private User staff;

    @BeforeEach
    void setUp() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        staff = users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Staff")
                .role(Role.PLATFORM_ADMIN).totpEnabled(true).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
    }

    @Test
    void companiesAlreadyHereWhenVerificationTurnsOnKeepPublishingAsLegacy() throws Exception {
        User earlyAdmin = user(Role.COMPANY_ADMIN);
        EnterpriseProfile early = company(earlyAdmin, "Early Co");
        User verifiedAdmin = user(Role.COMPANY_ADMIN);
        EnterpriseProfile verified = company(verifiedAdmin, "Verified Co");
        verifications.save(BusinessVerification.builder().tenant(verified).legalName("Verified Co").website("https://v.example")
                .domain("v.example").workEmail("a@v.example").submitterRole(BusinessVerification.SubmitterRole.FOUNDER)
                .status(BusinessVerification.Status.VERIFIED).verifiedAt(Instant.now()).build());

        // Creating the flag switched off grandfathers nobody; switching it on does.
        String flagId = id(call(staff, post("/admin/flags"),
                "{\"key\":\"company_verification_required\",\"label\":\"Verify before publishing\",\"enabled\":false}"));
        assertThat(enterprises.findById(early.getId()).orElseThrow().getVerificationGrandfatheredAt()).isNull();
        call(staff, put("/admin/flags/" + flagId), "{\"enabled\":true}").andExpect(status().isOk());

        assertThat(enterprises.findById(early.getId()).orElseThrow().getVerificationGrandfatheredAt()).isNotNull();
        assertThat(enterprises.findById(verified.getId()).orElseThrow().getVerificationGrandfatheredAt()).isNull();
        assertThat(auditCount("business.grandfathered")).isEqualTo(1);

        // A company that arrives after the switch must verify first.
        User lateAdmin = user(Role.COMPANY_ADMIN);
        EnterpriseProfile late = company(lateAdmin, "Late Co");
        call(lateAdmin, post("/enterprise/postings"), JOB).andExpect(status().isBadRequest());
        call(earlyAdmin, post("/enterprise/postings"), JOB).andExpect(status().isOk());
        call(verifiedAdmin, post("/enterprise/postings"), JOB).andExpect(status().isOk());

        call(earlyAdmin, get("/enterprise/verification"), null)
                .andExpect(jsonPath("$.data.status").value("verified_legacy"))
                .andExpect(jsonPath("$.data.legacy").value(true));
        call(lateAdmin, get("/enterprise/verification"), null)
                .andExpect(jsonPath("$.data.status").value("none"))
                .andExpect(jsonPath("$.data.legacy").value(false));
        // Legacy isn't the verified badge.
        mvc.perform(get("/companies/" + early.getId() + "/verification")).andExpect(jsonPath("$.data.verified").value(false));

        // The admin's review list, and ending legacy status after review.
        call(staff, get("/admin/verifications/legacy"), null)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].companyId").value(early.getId().toString()))
                .andExpect(jsonPath("$.data[0].verificationStatus").value("none"));
        call(earlyAdmin, put("/admin/verifications/legacy/" + early.getId() + "/end"), null).andExpect(status().isForbidden());
        call(staff, put("/admin/verifications/legacy/" + late.getId() + "/end"), null).andExpect(status().isBadRequest());
        call(staff, put("/admin/verifications/legacy/" + early.getId() + "/end"), null).andExpect(status().isOk());
        call(earlyAdmin, post("/enterprise/postings"), JOB).andExpect(status().isBadRequest());
        assertThat(auditCount("business.legacy_ended")).isEqualTo(1);
    }

    @Test
    void withTheFlagOffNobodyIsGrandfatheredOrBlocked() throws Exception {
        User admin = user(Role.COMPANY_ADMIN);
        EnterpriseProfile company = company(admin, "Quiet Co");
        call(admin, post("/enterprise/postings"), JOB).andExpect(status().isOk());
        call(admin, get("/enterprise/verification"), null).andExpect(jsonPath("$.data.status").value("none"));
        assertThat(enterprises.findById(company.getId()).orElseThrow().getVerificationGrandfatheredAt()).isNull();
    }

    @Test
    void theRetentionDryRunReportIsForPlatformAdminsOnly() throws Exception {
        call(staff, get("/admin/retention"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.deleted").value(false))
                .andExpect(jsonPath("$.data.applications").value(0));
        User admin = user(Role.COMPANY_ADMIN);
        call(admin, get("/admin/retention"), null).andExpect(status().isForbidden());
    }

    @Test
    void billingIsDisplayOnly() throws Exception {
        User admin = user(Role.COMPANY_ADMIN);
        company(admin, "Plan Co");
        call(admin, get("/enterprise/admin/billing"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plan").value("free"))
                .andExpect(jsonPath("$.data.invoices.length()").value(0));
        // No payments yet, so no self-service upgrade (it used to hand out paid seats for free).
        call(admin, put("/enterprise/admin/billing/plan"), "{\"plan\":\"pro\"}").andExpect(status().isBadRequest());
        call(admin, put("/enterprise/admin/billing/plan"), "{\"plan\":\"free\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.plan").value("free"));
    }

    @Test
    void ownerIsTheCompanyAdminUnderAnotherName() throws Exception {
        assertThat(Role.fromWireValue("owner")).isEqualTo(Role.COMPANY_ADMIN);
        User admin = user(Role.COMPANY_ADMIN);
        company(admin, "Owner Co");
        call(admin, get("/enterprise/team/roles"), null)
                .andExpect(jsonPath("$.data[0].role").value("company_admin"))
                .andExpect(jsonPath("$.data[0].label").value("Owner (company admin)"));
        // No separate "interviewer" role: hiring managers cover it (row 35 decision).
        call(admin, post("/enterprise/admin/team/invite"), "{\"email\":\"new@test.local\",\"role\":\"interviewer\"}")
                .andExpect(status().isBadRequest());
    }

    private long auditCount(String action) {
        em.flush();
        return jdbc.queryForObject("select count(*) from arena_audit_events where action = ?", Long.class, action);
    }

    private EnterpriseProfile company(User admin, String name) {
        return enterprises.save(EnterpriseProfile.builder().user(admin).companyName(name).logoEmoji("C")
                .industry(Industry.DESIGN).size(CompanySize.S_11_50).build());
    }

    private ResultActions call(User as, MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header("Authorization", "Bearer " + tokens.generateToken(as.getId(), as.getEmail(), as.getName(), as.getRole()));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }

    private String id(ResultActions result) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("id").asText();
    }

    private User user(Role role) {
        return users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Person")
                .role(role).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
    }
}
