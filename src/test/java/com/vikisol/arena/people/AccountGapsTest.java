package com.vikisol.arena.people;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.business.entity.BusinessVerification;
import com.vikisol.arena.business.repository.BusinessVerificationRepository;
import com.vikisol.arena.enterprise.entity.CompanySize;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.repository.PostRepository;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.entity.ConsentSettings;
import com.vikisol.arena.profile.entity.Industry;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import com.vikisol.arena.schema.EmbeddedPostgresAppTest;
import com.vikisol.arena.security.jwt.JwtTokenProvider;
import com.vikisol.arena.security.jwt.TokenDenylistService;
import jakarta.persistence.EntityManager;
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
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** FE-API-GAPS rows 43 and 46 (admin, in the B+ screens' terms) and 52-54 (account, B+). */
@AutoConfigureMockMvc
class AccountGapsTest extends EmbeddedPostgresAppTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired CandidateProfileRepository profiles;
    @Autowired EnterpriseProfileRepository enterprises;
    @Autowired BusinessVerificationRepository verifications;
    @Autowired PostRepository posts;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @MockBean TokenDenylistService denylist;
    @MockBean com.vikisol.arena.security.jwt.RefreshTokenService refreshTokens; // suspending revokes them (Redis)

    private User staff, asha, ravi;

    @BeforeEach
    void setUp() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        staff = users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Staff")
                .role(Role.PLATFORM_ADMIN).totpEnabled(true).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
        asha = talent("Asha");
        ravi = talent("Ravi");
    }

    // Row 43.
    @Test
    void theVerificationQueueAnswersInTheAdminScreensTerms() throws Exception {
        User admin = user(Role.COMPANY_ADMIN, "Owner");
        EnterpriseProfile company = enterprises.save(EnterpriseProfile.builder().user(admin).companyName("GreenLeaf").logoEmoji("G")
                .industry(Industry.DESIGN).size(CompanySize.S_11_50).build());
        BusinessVerification v = verifications.save(BusinessVerification.builder().tenant(company).legalName("GreenLeaf Pvt Ltd")
                .website("https://greenleaf.example").domain("greenleaf.example").workEmail("a@greenleaf.example")
                .submitterRole(BusinessVerification.SubmitterRole.FOUNDER).domainConfirmedAt(Instant.now()).build());

        call(staff, get("/admin/verification"), null)
                .andExpect(jsonPath("$.data[0].id").value(v.getId().toString()))
                .andExpect(jsonPath("$.data[0].domainMatch").value(true))
                .andExpect(jsonPath("$.data[0].submittedAt").isNotEmpty());
        call(staff, put("/admin/verification/" + v.getId() + "/reject"), "{}").andExpect(status().isBadRequest());
        call(staff, put("/admin/verification/" + v.getId() + "/reject"), "{\"reason\":\"Domain belongs to someone else\"}")
                .andExpect(jsonPath("$.data.status").value("rejected"))
                .andExpect(jsonPath("$.data.reviewNote").value("Domain belongs to someone else"));
        call(staff, get("/admin/verification").param("status", "approved"), null).andExpect(jsonPath("$.data.length()").value(0));
    }

    // Row 46.
    @Test
    void disputesUseTheScreensStatusesAndResolveBySide() throws Exception {
        String activity = id(call(asha, post("/posts"), "{\"intentType\":\"activity\",\"body\":\"Badminton\",\"startsAt\":\""
                + Instant.now().plus(2, ChronoUnit.DAYS) + "\"}"));
        String join = id(call(ravi, post("/posts/" + activity + "/joins"), null));
        Post p = posts.findById(UUID.fromString(activity)).orElseThrow();
        p.setStartsAt(Instant.now().minus(2, ChronoUnit.HOURS));
        posts.save(p);
        call(asha, put("/posts/" + activity + "/joins/" + join + "/outcome"), "{\"outcome\":\"no_show\"}").andExpect(status().isOk());
        call(ravi, post("/activities/" + activity + "/attendance/dispute"), "{\"reason\":\"I was on court 2\"}").andExpect(status().isOk());

        JsonNode open = body(call(staff, get("/admin/disputes").param("status", "open"), null)).path("data").get(0);
        assertThat(open.path("state").asText()).isEqualTo("open");
        assertThat(open.path("activityTitle").asText()).isEqualTo("Badminton");
        assertThat(open.path("hostName").asText()).isEqualTo("Asha");
        assertThat(open.path("joinerName").asText()).isEqualTo("Ravi");
        assertThat(open.path("note").asText()).isEqualTo("I was on court 2");
        assertThat(Instant.parse(open.path("deadlineAt").asText())).isAfter(Instant.parse(open.path("openedAt").asText()));
        String id = open.path("id").asText();

        // Past the 72-hour review window it shows as expired.
        call(staff, get("/admin/disputes").param("status", "expired"), null).andExpect(jsonPath("$.data.length()").value(0));
        em.flush();
        jdbc.update("update arena_activity_attendance set disputed_at = now() - interval '73 hours' where id = ?", UUID.fromString(id));
        em.clear();
        call(staff, get("/admin/disputes").param("status", "expired"), null)
                .andExpect(jsonPath("$.data[0].state").value("expired"));

        call(staff, put("/admin/disputes/" + id + "/resolve"), "{\"side\":\"referee\",\"reason\":\"x\"}").andExpect(status().isBadRequest());
        call(staff, put("/admin/disputes/" + id + "/resolve"), "{\"side\":\"joiner\"}").andExpect(status().isBadRequest());
        call(staff, put("/admin/disputes/" + id + "/resolve"), "{\"side\":\"joiner\",\"reason\":\"Photo shows him playing\"}")
                .andExpect(jsonPath("$.data.state").value("resolved_joiner"));
        call(staff, get("/admin/disputes").param("status", "resolved_joiner"), null).andExpect(jsonPath("$.data.length()").value(1));
        em.flush();
        assertThat(jdbc.queryForObject("select count(*) from arena_audit_events where action = 'dispute.resolved'", Long.class)).isEqualTo(1);
    }

    // Row 52.
    @Test
    void notificationPreferencesTakeThePluralNamesAndNewToggles() throws Exception {
        call(asha, get("/notifications/preferences"), null)
                .andExpect(jsonPath("$.data.messages").value(true))
                .andExpect(jsonPath("$.data.jenny").value(true))
                .andExpect(jsonPath("$.data.marketing").value(false)); // opt-in
        call(asha, put("/notifications/preferences"), "{\"messages\":false,\"marketing\":true,\"jenny\":false}")
                .andExpect(jsonPath("$.data.messages").value(false))
                .andExpect(jsonPath("$.data.message").value(false))
                .andExpect(jsonPath("$.data.marketing").value(true))
                .andExpect(jsonPath("$.data.jenny").value(false))
                .andExpect(jsonPath("$.data.activities").value(true));
        // The original singular names still work.
        call(asha, put("/notifications/preferences"), "{\"message\":true}").andExpect(jsonPath("$.data.messages").value(true));
    }

    // Row 53.
    @Test
    void patchProfileTakesInterestsAndOnlyRemovesAPhoto() throws Exception {
        call(asha, patch("/profile/me"), "{\"interests\":[\"Cricket\",\"cricket\",\"Pottery\"],\"title\":\"Designer\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interests.length()").value(2));
        call(asha, patch("/profile/me"), "{\"photoUrl\":\"https://tracker.example/pixel.png\"}").andExpect(status().isBadRequest());
        call(asha, patch("/profile/me"), "{\"photoUrl\":\"\"}").andExpect(status().isOk());
    }

    // Row 54.
    @Test
    void aProfileHonoursItsVisibility() throws Exception {
        String url = "/profile/" + asha.getId();
        call(asha, put("/profile/me/visibility"), "{\"profile\":\"hidden\"}").andExpect(status().isOk());
        call(ravi, get(url), null).andExpect(status().isNotFound());
        mvc.perform(get(url)).andExpect(status().isNotFound());
        call(asha, get(url), null).andExpect(status().isOk());

        call(asha, put("/profile/me/visibility"), "{\"profile\":\"nearby\"}").andExpect(status().isOk());
        mvc.perform(get(url)).andExpect(status().isNotFound());
        call(ravi, get(url), null).andExpect(status().isOk());

        call(asha, put("/profile/me/visibility"), "{\"profile\":\"everyone\"}").andExpect(status().isOk());
        mvc.perform(get(url)).andExpect(status().isOk());
        call(asha, post("/blocks/" + ravi.getId()), null).andExpect(status().isOk());
        call(ravi, get(url), null).andExpect(status().isNotFound());
    }

    // Row 61.
    @Test
    void aPersonCanBeReportedAndActedOn() throws Exception {
        String profileId = profiles.findByUserId(ravi.getId()).orElseThrow().getId().toString();
        call(asha, post("/profile/" + asha.getId() + "/report"), "{\"reason\":\"Me\"}").andExpect(status().isBadRequest());
        call(asha, post("/profile/" + UUID.randomUUID() + "/report"), "{\"reason\":\"Who\"}").andExpect(status().isNotFound());
        call(asha, post("/profile/" + ravi.getId() + "/report"), "{}").andExpect(status().isBadRequest());
        mvc.perform(post("/profile/" + ravi.getId() + "/report").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        // By profile id works too; a second open report from the same person is refused.
        call(asha, post("/profile/" + profileId + "/report"), "{\"reason\":\"Keeps messaging me after I said no\"}")
                .andExpect(status().isOk());
        call(asha, post("/profile/" + ravi.getId() + "/report"), "{\"reason\":\"Again\"}").andExpect(status().isBadRequest());

        JsonNode item = body(call(staff, get("/admin/moderation"), null)).path("data").path("content").get(0);
        assertThat(item.path("contentType").asText()).isEqualTo("user");
        assertThat(item.path("reportedUserId").asText()).isEqualTo(ravi.getId().toString());
        assertThat(item.path("reason").asText()).isEqualTo("Keeps messaging me after I said no");
        String id = item.path("id").asText();
        call(staff, put("/admin/moderation/" + id + "/takedown"), null).andExpect(status().isBadRequest());
        call(staff, put("/admin/moderation/" + id + "/suspend"), "{\"reason\":\"Harassment\",\"durationDays\":3}")
                .andExpect(jsonPath("$.data.id").value(ravi.getId().toString()))
                .andExpect(jsonPath("$.data.reportsAgainst").value(1));
    }

    private User talent(String name) {
        User u = user(Role.TALENT, name);
        profiles.save(CandidateProfile.builder().user(u).name(name).avatarEmoji("*").title("Designer").industry(Industry.DESIGN)
                .location("Hyderabad").remote(false).experienceYears(3).consent(new ConsentSettings(false, true)).build());
        return u;
    }

    private User user(Role role, String name) {
        return users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name(name)
                .role(role).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
    }

    private ResultActions call(User as, MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header("Authorization", "Bearer " + tokens.generateToken(as.getId(), as.getEmail(), as.getName(), as.getRole()));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String id(ResultActions result) throws Exception {
        return body(result.andExpect(status().isOk())).path("data").path("id").asText();
    }
}
