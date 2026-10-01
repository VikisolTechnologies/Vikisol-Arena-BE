package com.vikisol.arena.people;

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
import com.vikisol.arena.jobs.entity.EmploymentType;
import com.vikisol.arena.jobs.entity.JobPosting;
import com.vikisol.arena.jobs.repository.JobPostingRepository;
import com.vikisol.arena.notifications.repository.NotificationRepository;
import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.repository.PostRepository;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FE-API-GAPS rows 15, 16, 17, 18, 34, 37 and the admin disputes queue (flow §9). */
@AutoConfigureMockMvc
class PeopleAppTest extends EmbeddedPostgresAppTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired CandidateProfileRepository profiles;
    @Autowired EnterpriseProfileRepository enterprises;
    @Autowired JobPostingRepository postings;
    @Autowired ApplicationRepository applications;
    @Autowired NotificationRepository notifications;
    @Autowired PostRepository posts;
    @MockBean TokenDenylistService denylist;

    private User asha, ravi, meera, recruiter;

    @BeforeEach
    void setUp() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        asha = talent("Asha");
        ravi = talent("Ravi");
        meera = talent("Meera");
        recruiter = user(Role.COMPANY_ADMIN, "Recruiter");
    }

    @Test
    void reportsCarryTheReportersOwnEvidence() throws Exception {
        String post = body(call(ravi, post("/posts"), "{\"intentType\":\"update\",\"body\":\"Something odd\"}")).path("data").path("id").asText();
        String url = body(call(asha, multipart("/reports/evidence").file(png()), null).andExpect(status().isOk())).path("data").path("url").asText();
        String ravisUpload = body(call(ravi, multipart("/reports/evidence").file(png()), null)).path("data").path("url").asText();
        call(asha, post("/posts/" + post + "/report"), "{\"reason\":\"Spam\",\"evidenceUrls\":[\"" + ravisUpload + "\"]}")
                .andExpect(status().isBadRequest());
        call(asha, post("/posts/" + post + "/report"), "{\"reason\":\"Spam\",\"evidenceUrls\":[\"https://evil.example/x.png\"]}")
                .andExpect(status().isBadRequest());
        call(asha, post("/posts/" + post + "/report"), "{\"reason\":\"Spam\",\"evidenceUrls\":[\"" + url + "\"]}").andExpect(status().isOk());
        call(admin(), get("/admin/moderation"), null)
                .andExpect(jsonPath("$.data.content[0].reason").value("Spam"))
                .andExpect(jsonPath("$.data.content[0].evidenceUrls[0]").value(containsString("/report-evidence/" + asha.getId() + "/")));
    }

    @Test
    void notificationsHaveCategoriesSnoozeDismissAndPreferences() throws Exception {
        String need = body(call(asha, post("/posts"), "{\"intentType\":\"ask\",\"body\":\"Need a ladder\"}")).path("data").path("id").asText();
        call(ravi, post("/needs/" + need + "/responses"), "{\"message\":\"I have one\"}").andExpect(status().isOk());
        JsonNode list = body(call(asha, get("/notifications"), null)).path("data").path("content");
        assertThat(list.get(0).path("category").asText()).isEqualTo("need");
        String id = list.get(0).path("id").asText();

        call(asha, post("/notifications/" + id + "/snooze"), "{\"minutes\":5}").andExpect(status().isBadRequest());
        call(ravi, post("/notifications/" + id + "/snooze"), null).andExpect(status().isForbidden());
        call(asha, post("/notifications/" + id + "/snooze"), "{\"minutes\":60}").andExpect(status().isOk());
        call(asha, get("/notifications"), null).andExpect(jsonPath("$.data.content[*].id", not(hasItem(id))));

        call(asha, put("/notifications/preferences"), "{\"safety\":false}").andExpect(status().isBadRequest());
        call(asha, put("/notifications/preferences"), "{\"need\":false}")
                .andExpect(jsonPath("$.data.need").value(false)).andExpect(jsonPath("$.data.activity").value(true));
        long before = notifications.findByUserIdOrderByCreatedAtDesc(asha.getId(), PageRequest.of(0, 50)).getTotalElements();
        call(meera, post("/needs/" + need + "/responses"), "{\"message\":\"Me too\"}").andExpect(status().isOk());
        assertThat(notifications.findByUserIdOrderByCreatedAtDesc(asha.getId(), PageRequest.of(0, 50)).getTotalElements()).isEqualTo(before);

        call(asha, post("/notifications/" + id + "/dismiss"), null).andExpect(status().isOk());
        assertThat(notifications.findById(UUID.fromString(id))).isEmpty();
    }

    @Test
    void peopleSearchFollowsProfileVisibilityDistanceAndBlocks() throws Exception {
        locate(ravi, 17.44, 78.35);   // ~1 km from asha
        locate(meera, 17.70, 78.60);  // ~40 km from asha
        call(asha, get("/search").param("type", "people").param("q", "figma"), null)
                .andExpect(jsonPath("$.data.people.length()").value(2));
        mvc.perform(get("/search").param("type", "people").param("q", "figma")).andExpect(jsonPath("$.data.people.length()").value(0));

        // ARCHITECT-REVIEW-BE-1 blocker #1: `near` can only search near the caller's own stored
        // location now, never an arbitrary point - asha must locate herself first, and a bare
        // "near=true" (no coordinates accepted) is the entire request.
        locate(asha, 17.44, 78.34);
        call(asha, get("/search").param("type", "skills").param("q", "figma").param("near", "true").param("radiusKm", "5"), null)
                .andExpect(jsonPath("$.data.people.length()").value(1))
                .andExpect(jsonPath("$.data.people[0].name").value("Ravi"))
                .andExpect(jsonPath("$.data.people[0].distanceBand").value("within 2 km"));
        // Coordinates in the `near` value are ignored outright - still anchors to asha, not to meera.
        call(asha, get("/search").param("type", "skills").param("q", "figma").param("near", "17.70,78.60").param("radiusKm", "5"), null)
                .andExpect(jsonPath("$.data.people[*].name", hasItem("Ravi")))
                .andExpect(jsonPath("$.data.people[*].name", not(hasItem("Meera"))));
        // Below the 2 km radius floor, the search circle still never shrinks past 2 km.
        call(asha, get("/search").param("type", "skills").param("q", "figma").param("near", "true").param("radiusKm", "0.1"), null)
                .andExpect(jsonPath("$.data.people[0].name").value("Ravi"));

        call(ravi, put("/profile/me/visibility"), "{\"profile\":\"nearby\"}").andExpect(jsonPath("$.data.profile").value("nearby"));
        call(asha, get("/search").param("type", "people").param("q", "figma"), null)
                .andExpect(jsonPath("$.data.people[*].name", not(hasItem("Ravi"))));
        call(asha, get("/search").param("type", "people").param("q", "figma").param("near", "true"), null)
                .andExpect(jsonPath("$.data.people[*].name", hasItem("Ravi")));
        call(ravi, put("/profile/me/visibility"), "{\"profile\":\"hidden\"}").andExpect(status().isOk());
        call(asha, get("/search").param("type", "people").param("q", "figma").param("near", "true"), null)
                .andExpect(jsonPath("$.data.people.length()").value(0));
        call(ravi, put("/profile/me/visibility"), "{\"profile\":\"invisible\"}").andExpect(status().isBadRequest());

        call(asha, post("/blocks/" + meera.getId()), null).andExpect(status().isOk());
        call(asha, get("/search").param("type", "people").param("q", "figma"), null)
                .andExpect(jsonPath("$.data.people.length()").value(0));

        // An unlocated viewer (recruiter has no CandidateProfile at all) searching "near" gets an
        // empty result, not an error or someone else's area.
        call(recruiter, get("/search").param("type", "people").param("q", "figma").param("near", "true"), null)
                .andExpect(jsonPath("$.data.people.length()").value(0));
    }

    @Test
    void employersAskFirstAndMessageOnlyAfterAcceptOrApply() throws Exception {
        EnterpriseProfile acme = enterprises.save(EnterpriseProfile.builder().user(recruiter).companyName("Acme").logoEmoji("A")
                .industry(Industry.DESIGN).size(CompanySize.S_11_50).build());
        CandidateProfile ravisProfile = profiles.findByUserId(ravi.getId()).orElseThrow();
        String startChat = "{\"participantUserId\":\"" + asha.getId() + "\"}";
        call(recruiter, post("/messages/conversations"), startChat).andExpect(status().isBadRequest());

        String requestId = body(call(recruiter, post("/enterprise/talent/" + profiles.findByUserId(asha.getId()).orElseThrow().getId() + "/connect"),
                "{\"note\":\"We're hiring designers in Hyderabad\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("pending"))).path("data").path("id").asText();
        call(asha, get("/connect-requests"), null)
                .andExpect(jsonPath("$.data[0].companyName").value("Acme"))
                .andExpect(jsonPath("$.data[0].note").value("We're hiring designers in Hyderabad"));
        call(ravi, post("/connect-requests/" + requestId + "/accept"), null).andExpect(status().isNotFound());
        call(asha, post("/connect-requests/" + requestId + "/accept"), null)
                .andExpect(jsonPath("$.data.status").value("accepted"))
                .andExpect(jsonPath("$.data.conversationId").isNotEmpty());
        call(recruiter, post("/messages/conversations"), startChat).andExpect(status().isOk());

        // Declined: no second request. Applying opens messaging on its own.
        String second = body(call(recruiter, post("/enterprise/talent/" + ravisProfile.getId() + "/connect"), "{\"note\":\"Hi\"}"))
                .path("data").path("id").asText();
        call(ravi, post("/connect-requests/" + second + "/decline"), null).andExpect(jsonPath("$.data.status").value("declined"));
        call(recruiter, post("/enterprise/talent/" + ravisProfile.getId() + "/connect"), "{\"note\":\"Please?\"}").andExpect(status().isBadRequest());
        JobPosting job = postings.save(JobPosting.builder().enterprise(acme).title("Designer").industry(Industry.DESIGN).location("Hyderabad")
                .remote(false).employmentType(EmploymentType.FULL_TIME).salaryMin(1).salaryMax(2).description("Design").build());
        applications.save(Application.builder().candidate(ravisProfile).jobPosting(job).appliedAt(Instant.now()).build());
        call(recruiter, post("/messages/conversations"), "{\"participantUserId\":\"" + ravi.getId() + "\"}").andExpect(status().isOk());
    }

    @Test
    void conversationsShowALastMessagePreview() throws Exception {
        String id = body(call(asha, post("/messages/conversations"), "{\"participantUserId\":\"" + ravi.getId() + "\"}")).path("data").path("id").asText();
        call(asha, get("/messages/conversations"), null).andExpect(jsonPath("$.data[0].lastMessagePreview").doesNotExist());
        call(asha, post("/messages/conversations/" + id + "/messages"), "{\"content\":\"First\"}").andExpect(status().isOk());
        call(ravi, post("/messages/conversations/" + id + "/messages"), "{\"content\":\"" + "x".repeat(200) + "\"}").andExpect(status().isOk());
        call(asha, get("/messages/conversations"), null)
                .andExpect(jsonPath("$.data[0].lastMessagePreview").value("x".repeat(139) + "…"));
    }

    @Test
    void anAdminDecidesAttendanceDisputes() throws Exception {
        String id = body(call(asha, post("/posts"), "{\"intentType\":\"activity\",\"body\":\"Badminton\",\"startsAt\":\""
                + Instant.now().plus(2, ChronoUnit.DAYS) + "\"}")).path("data").path("id").asText();
        String join = body(call(ravi, post("/posts/" + id + "/joins"), null)).path("data").path("id").asText();
        Post p = posts.findById(UUID.fromString(id)).orElseThrow();
        p.setStartsAt(Instant.now().minus(2, ChronoUnit.HOURS));
        posts.save(p);
        call(asha, put("/posts/" + id + "/joins/" + join + "/outcome"), "{\"outcome\":\"no_show\"}").andExpect(status().isOk());
        call(ravi, post("/activities/" + id + "/attendance/dispute"), "{\"reason\":\"I was on court 2\"}").andExpect(status().isOk());

        User admin = admin();
        JsonNode queue = body(call(admin, get("/admin/disputes"), null)).path("data");
        assertThat(queue).hasSize(1);
        assertThat(queue.get(0).path("disputeReason").asText()).isEqualTo("I was on court 2");
        String attendance = queue.get(0).path("attendanceId").asText();
        call(asha, get("/admin/disputes"), null).andExpect(status().isForbidden());
        call(admin, put("/admin/disputes/" + attendance + "/reject"), "{}").andExpect(status().isBadRequest());
        call(admin, put("/admin/disputes/" + attendance + "/reject"), "{\"note\":\"The host's check-in list shows otherwise\"}")
                .andExpect(jsonPath("$.data.status").value("rejected"));
        call(admin, put("/admin/disputes/" + attendance + "/accept"), null).andExpect(status().isBadRequest());
        call(admin, get("/admin/disputes").param("status", "rejected"), null).andExpect(jsonPath("$.data[0].resolutionNote").exists());
        // The upheld no-show is final: it no longer counts as a join.
        mvc.perform(get("/profile/" + ravi.getId() + "/stats")).andExpect(jsonPath("$.data.joined").value(0));
    }

    private void locate(User u, double lat, double lng) {
        CandidateProfile p = profiles.findByUserId(u.getId()).orElseThrow();
        p.setLocationConsent(LocationConsent.CITY);
        p.setApproxLat(lat);
        p.setApproxLng(lng);
        profiles.save(p);
    }

    private static MockMultipartFile png() {
        return new MockMultipartFile("file", "shot.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0});
    }

    private User admin() {
        return users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Staff")
                .role(Role.PLATFORM_ADMIN).totpEnabled(true).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
    }

    private User talent(String name) {
        User u = user(Role.TALENT, name);
        profiles.save(CandidateProfile.builder().user(u).name(name).avatarEmoji("*").title("Designer").industry(Industry.DESIGN)
                .location("Hyderabad").remote(false).experienceYears(3)
                .skills(new ArrayList<>(List.of(new CandidateSkill("Figma", true))))
                .consent(new ConsentSettings(false, true)).build());
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
