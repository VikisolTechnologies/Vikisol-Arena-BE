package com.vikisol.arena.platform;

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
import com.vikisol.arena.notifications.repository.NotificationRepository;
import com.vikisol.arena.posts.entity.PostStatus;
import com.vikisol.arena.posts.repository.PostRepository;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.entity.ConsentSettings;
import com.vikisol.arena.profile.entity.Industry;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import com.vikisol.arena.schema.EmbeddedPostgresAppTest;
import com.vikisol.arena.security.jwt.JwtTokenProvider;
import com.vikisol.arena.security.jwt.RefreshTokenService;
import com.vikisol.arena.security.jwt.TokenDenylistService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** FE-API-GAPS rows 42-51 (admin, B+): account actions, sessions, metrics, content, audit, team, Jenny. */
@AutoConfigureMockMvc
class AdminAccountGapsTest extends EmbeddedPostgresAppTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider tokens;
    @Autowired com.vikisol.arena.security.service.TotpService totp;
    @Autowired UserRepository users;
    @Autowired CandidateProfileRepository profiles;
    @Autowired EnterpriseProfileRepository enterprises;
    @Autowired JobPostingRepository postings;
    @Autowired PostRepository posts;
    @Autowired NotificationRepository notifications;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @MockBean TokenDenylistService denylist;
    @MockBean RefreshTokenService refreshTokens;

    private User staff, asha, ravi;
    // ARCHITECT-REVIEW-BE-1 (architect notes on B8/B9): launchMetricsCountRealActivityOnly
    // asserted an absolute signUps count, which only ever matched running this class alone - in
    // the full suite, other test classes' (non-demo, non-admin) users are already in the shared
    // embedded Postgres by the time this one runs, so the real count is always
    // signUpsBaseline + however many this test itself creates, never a fixed number. Captured
    // before setUp() creates its own fixtures, with production's exact filter.
    private long signUpsBaseline, activitiesCreatedBaseline, activitiesJoinedBaseline, reportsBaseline;

    @BeforeEach
    void setUp() {
        signUpsBaseline = jdbc.queryForObject(
                "select count(*) from arena_users where demo_content = false and role <> 'PLATFORM_ADMIN'", Long.class);
        activitiesCreatedBaseline = jdbc.queryForObject(
                "select count(*) from arena_posts where intent_type = 'ACTIVITY' and demo_content = false", Long.class);
        activitiesJoinedBaseline = jdbc.queryForObject("""
                select count(*) from arena_post_joins j join arena_posts p on p.id = j.post_id
                where p.intent_type = 'ACTIVITY' and j.status = 'APPROVED' and j.demo_content = false
                """, Long.class);
        reportsBaseline = jdbc.queryForObject(
                "select count(*) from arena_moderation_items where demo_content = false", Long.class);
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        when(refreshTokens.issue(any())).thenReturn("refresh-token");
        staff = users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Staff")
                .role(Role.PLATFORM_ADMIN).totpEnabled(true).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
        asha = talent("Asha");
        ravi = talent("Ravi");
    }

    // --- rows 50-51: accounts and sessions ---

    @Test
    void aSuspendedAccountIsSignedOutAndCantSignInUntilRestored() throws Exception {
        String token = token(asha);
        call(token, get("/profile/me/basics"), null).andExpect(status().isOk());

        call(staff, put("/admin/users/" + asha.getId() + "/suspend"), "{}").andExpect(status().isBadRequest()); // a reason is required
        call(asha, put("/admin/users/" + ravi.getId() + "/suspend"), "{\"reason\":\"x\"}").andExpect(status().isForbidden());
        call(staff, put("/admin/users/" + asha.getId() + "/suspend"), "{\"reason\":\"Harassment reports\",\"durationDays\":7}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("suspended"))
                .andExpect(jsonPath("$.data.suspensionReason").value("Harassment reports"));
        verify(refreshTokens).revokeAllForUser(asha.getId());

        // The token she already had stops working, and so does signing in.
        call(token, get("/profile/me/basics"), null).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/signin").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + asha.getEmail() + "\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This account is suspended. Contact Vikisol support for help."));

        call(staff, put("/admin/users/" + asha.getId() + "/restore"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("active"));
        mvc.perform(post("/auth/signin").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + asha.getEmail() + "\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isOk());
        assertThat(audits("user.suspended")).isEqualTo(1);
        assertThat(audits("user.restored")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select metadata from arena_audit_events where action = 'user.suspended'", String.class))
                .isEqualTo("Harassment reports (7 days)");
    }

    // ARCHITECT-REVIEW-BE-1 blocker #6: erasure used to keep the account's real email, phone,
    // handle and password hash - only the display name changed - and sign-in only checked
    // isBlocked(), not deletedAt, so an erased account's original credentials still worked.
    @Test
    void erasureTombstonesTheRealIdentityAndTheOldCredentialsStopWorking() throws Exception {
        // A unique X-Forwarded-For keeps these /auth/* calls off the shared IP-keyed rate-limit
        // bucket (10/min) that every other test's /auth/* calls also share in a full-suite run.
        String ip = "10.88." + (int) (Math.random() * 255) + "." + (int) (Math.random() * 255);
        String originalEmail = asha.getEmail();
        call(staff, delete("/admin/users/" + asha.getId()), null).andExpect(status().isOk());

        User erased = users.findById(asha.getId()).orElseThrow();
        assertThat(erased.getEmail()).isNotEqualTo(originalEmail);
        assertThat(erased.getHandle()).isNull();
        assertThat(erased.getPhoneNumber()).isNull();
        assertThat(passwords.matches("correct-horse", erased.getPasswordHash())).isFalse();
        assertThat(erased.getDeletedAt()).isNotNull();

        // The old email doesn't resolve to anyone any more.
        mvc.perform(post("/auth/signin").header("X-Forwarded-For", ip).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + originalEmail + "\",\"password\":\"correct-horse\"}"))
                .andExpect(status().isBadRequest());

        // Audit the id, never "Name (email)" - the thing it's logging just got erased.
        assertThat(jdbc.queryForObject("select target from arena_audit_events where action = 'account.erased_by_admin'", String.class))
                .isEqualTo(asha.getId().toString());

        // issueSession() itself must also refuse a deletedAt account, not just signIn()'s own
        // upfront check - AuthService.verifyMfa (the 2FA-completion step) looks the user up by id
        // from the pending token and goes straight to issueSession(), with no deletedAt check of
        // its own, so an account erased/banned between "enter password" and "enter 2FA code"
        // would otherwise still complete the session.
        String totpSecret = totp.generateSecret();
        User mfaUser = users.save(User.builder().email(UUID.randomUUID() + "@test.local")
                .passwordHash(passwords.encode("correct-horse")).name("Mid-MFA").role(Role.PLATFORM_ADMIN)
                .totpEnabled(true).totpSecret(totpSecret).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
        String pendingToken = tokens.generateMfaPendingToken(mfaUser.getId());
        long step = Instant.now().getEpochSecond() / 30;
        java.lang.reflect.Method generateCode = com.vikisol.arena.security.service.TotpService.class
                .getDeclaredMethod("generateCode", String.class, long.class);
        generateCode.setAccessible(true);
        String code = (String) generateCode.invoke(totp, totpSecret, step);
        mfaUser.setDeletedAt(Instant.now());
        users.save(mfaUser);
        mvc.perform(post("/auth/2fa/verify").header("X-Forwarded-For", ip).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pendingToken\":\"" + pendingToken + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This account can't sign in."));
    }

    @Test
    void forceSignOutEndsOlderSessionsOnly() throws Exception {
        String before = token(asha);
        Thread.sleep(1100); // tokens carry whole seconds
        call(staff, post("/admin/users/" + asha.getId() + "/force-signout"), "{\"reason\":\"Lost phone\"}").andExpect(status().isOk());
        call(before, get("/profile/me/basics"), null).andExpect(status().isUnauthorized());
        Thread.sleep(1100);
        call(token(asha), get("/profile/me/basics"), null).andExpect(status().isOk());
        assertThat(audits("user.signed_out")).isEqualTo(1);
    }

    // ARCHITECT-REVIEW-BE-1 SHOULD-FIX: a token minted in the exact same second as the force
    // sign-out used to read as "not before" sessionsRevokedAt and stay valid. Deterministic
    // same-second case (no sleeping and hoping): sessionsRevokedAt is set to literally the
    // token's own iat, which must now be rejected (strictly after is required).
    @Test
    void forceSignOutRejectsATokenIssuedInTheExactSameSecond() throws Exception {
        String sameSecond = token(asha);
        var claims = tokens.sessionClaims(sameSecond).orElseThrow();
        User fresh = users.findById(asha.getId()).orElseThrow();
        fresh.setSessionsRevokedAt(claims.getIssuedAt().toInstant());
        users.save(fresh);
        call(sameSecond, get("/profile/me/basics"), null).andExpect(status().isUnauthorized());
    }

    @Test
    void staffAccountsAndYourOwnAreOutOfReach() throws Exception {
        User other = users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Staff 2")
                .role(Role.PLATFORM_ADMIN).totpEnabled(true).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
        call(staff, put("/admin/users/" + other.getId() + "/suspend"), "{\"reason\":\"x\"}").andExpect(status().isBadRequest());
        call(staff, put("/admin/users/" + staff.getId() + "/suspend"), "{\"reason\":\"x\"}").andExpect(status().isBadRequest());
        // An admin without 2FA is stopped by the admin 2FA filter.
        User no2fa = users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("New staff")
                .role(Role.PLATFORM_ADMIN).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
        call(no2fa, get("/admin/users/" + asha.getId()), null).andExpect(status().isForbidden());
    }

    @Test
    void theAccountDetailHasFlagsButNoSecrets() throws Exception {
        call(asha, get("/profile/me/export"), null).andExpect(status().isOk());
        String body = call(staff, get("/admin/users/" + asha.getId()), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("active"))
                .andExpect(jsonPath("$.data.lastDataExportAt").isNotEmpty())
                .andExpect(jsonPath("$.data.deletionRequested").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("passwordHash").doesNotContain("totpSecret").doesNotContain(asha.getPasswordHash());
    }

    @Test
    void reportsLeadToWarningsAndBans() throws Exception {
        String post = id(call(ravi, post("/posts"), "{\"intentType\":\"update\",\"body\":\"Buy followers here\"}"));
        call(asha, post("/posts/" + post + "/report"), "{\"reason\":\"Spam\"}").andExpect(status().isOk());
        em.flush();
        String item = jdbc.queryForObject("select id from arena_moderation_items where post_id = ?", String.class, UUID.fromString(post));

        call(staff, put("/admin/moderation/" + item + "/warn"), "{\"reason\":\"Please don't post adverts\"}").andExpect(status().isOk());
        assertThat(notifications.findAll()).anyMatch(n -> n.getUser().getId().equals(ravi.getId())
                && n.getBody().equals("Please don't post adverts"));
        call(staff, put("/admin/moderation/" + item + "/ban"), "{}").andExpect(status().isBadRequest());
        call(staff, put("/admin/moderation/" + item + "/ban"), "{\"reason\":\"Repeated spam\"}")
                .andExpect(jsonPath("$.data.status").value("banned"))
                .andExpect(jsonPath("$.data.id").value(ravi.getId().toString()));
        call(ravi, get("/profile/me/basics"), null).andExpect(status().isUnauthorized());
        assertThat(audits("user.warned")).isEqualTo(1);
        assertThat(audits("user.banned")).isEqualTo(1);
        // Dismissing and taking down are audited too now, with the reason when given.
        call(staff, put("/admin/moderation/" + item + "/takedown"), "{\"reason\":\"Spam\"}").andExpect(status().isOk());
        assertThat(audits("moderation.takedown")).isEqualTo(1);
    }

    // --- row 42 ---

    @Test
    void launchMetricsCountRealActivityOnly() throws Exception {
        call(staff, get("/admin/metrics/launch"), null)
                .andExpect(jsonPath("$.data.signUps").value(signUpsBaseline + 2))
                .andExpect(jsonPath("$.data.onboardingCompleted").doesNotExist())
                .andExpect(jsonPath("$.data.d1ReturnRate").doesNotExist());
        String activity = id(call(asha, post("/posts"), "{\"intentType\":\"activity\",\"body\":\"Badminton\",\"startsAt\":\""
                + Instant.now().plusSeconds(86400 * 2) + "\"}"));
        call(ravi, post("/posts/" + activity + "/joins"), null).andExpect(status().isOk());
        call(ravi, post("/posts/" + activity + "/report"), "{\"reason\":\"Test\"}").andExpect(status().isOk());
        User demo = User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Demo").role(Role.TALENT)
                .dateOfBirth(LocalDate.of(1990, 1, 1)).build();
        demo.setDemoContent(true);
        users.save(demo);
        em.flush();
        // Return rates: Asha signed up two days ago and came back the next day; Ravi didn't.
        jdbc.update("update arena_users set created_at = now() - interval '2 days' where id in (?, ?)", asha.getId(), ravi.getId());
        jdbc.update("insert into arena_user_active_days (user_id, day) values (?, (now() at time zone 'utc')::date - 2), (?, (now() at time zone 'utc')::date - 1), (?, (now() at time zone 'utc')::date - 2)",
                asha.getId(), asha.getId(), ravi.getId());
        call(staff, get("/admin/metrics/launch"), null)
                .andExpect(jsonPath("$.data.signUps").value(signUpsBaseline + 2))
                .andExpect(jsonPath("$.data.activitiesCreated").value(activitiesCreatedBaseline + 1))
                .andExpect(jsonPath("$.data.activitiesJoined").value(activitiesJoinedBaseline + 1))
                .andExpect(jsonPath("$.data.reportsTotal").value(reportsBaseline + 1))
                .andExpect(jsonPath("$.data.d1ReturnRate").value(0.5))
                .andExpect(jsonPath("$.data.d7ReturnRate").doesNotExist());
        call(asha, get("/admin/metrics/launch"), null).andExpect(status().isForbidden());
    }

    // --- rows 44-45 ---

    @Test
    void contentCanBeBrowsedAndTakenDown() throws Exception {
        String need = id(call(asha, post("/posts"), "{\"intentType\":\"ask\",\"body\":\"Need a ladder in Kondapur\"}"));
        call(ravi, post("/posts/" + need + "/report"), "{\"reason\":\"Scam\"}").andExpect(status().isOk());
        EnterpriseProfile acme = enterprises.save(EnterpriseProfile.builder().user(user(Role.COMPANY_ADMIN, "Acme admin"))
                .companyName("Acme").logoEmoji("A").industry(Industry.DESIGN).size(CompanySize.S_11_50).build());
        JobPosting job = postings.save(JobPosting.builder().enterprise(acme).title("Ladder designer").industry(Industry.DESIGN)
                .location("Hyderabad").remote(false).employmentType(EmploymentType.FULL_TIME).salaryMin(1).salaryMax(2)
                .description("Design ladders").build());

        call(staff, get("/admin/content").param("query", "ladder"), null)
                .andExpect(jsonPath("$.data.length()").value(2));
        call(staff, get("/admin/content").param("kind", "need"), null)
                .andExpect(jsonPath("$.data[0].id").value(need))
                .andExpect(jsonPath("$.data[0].authorName").value("Asha"))
                .andExpect(jsonPath("$.data[0].reportCount").value(1));
        call(staff, get("/admin/content").param("kind", "video"), null).andExpect(status().isBadRequest());

        call(staff, put("/admin/content/" + need + "/takedown"), "{}").andExpect(status().isBadRequest());
        call(staff, put("/admin/content/" + need + "/takedown"), "{\"reason\":\"Scam report confirmed\"}")
                .andExpect(jsonPath("$.data.status").value("cancelled"));
        assertThat(posts.findById(UUID.fromString(need)).orElseThrow().getStatus()).isEqualTo(PostStatus.CANCELLED);
        assertThat(jdbc.queryForObject("select status from arena_moderation_items where post_id = ?", String.class, UUID.fromString(need)))
                .isEqualTo("TAKEN_DOWN");
        call(staff, put("/admin/content/" + job.getId() + "/takedown"), "{\"reason\":\"Fake job\"}")
                .andExpect(jsonPath("$.data.status").value("closed"));
        assertThat(audits("content.takedown")).isEqualTo(2);

        call(staff, get("/admin/catalog/activity-types"), null)
                .andExpect(jsonPath("$.data[0].category").value("sports"))
                .andExpect(jsonPath("$.data[0].subtypes[0]").value("cricket"));
    }

    // --- rows 47-49 ---

    @Test
    void theAuditLogCoversThePlatformAndExportsSafely() throws Exception {
        call(staff, put("/admin/users/" + asha.getId() + "/suspend"), "{\"reason\":\"=HYPERLINK(\\\"x\\\")\"}").andExpect(status().isOk());
        call(staff, get("/admin/audit").param("action", "user.suspended"), null)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].actorName").value("Staff"));
        String csv = call(staff, get("/admin/audit/export"), null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(csv).startsWith("Time,Actor,Action,Target,Metadata").contains("\"'=HYPERLINK(\"\"x\"\")");
        call(ravi, get("/admin/audit"), null).andExpect(status().isForbidden()); // (Asha is suspended: signed out)
        // ARCHITECT-REVIEW-BE-1 SHOULD-FIX: pulling the audit trail as CSV is itself audited.
        assertThat(audits("audit.exported")).isEqualTo(1);
    }

    @Test
    void theTeamListShowsStaffWithTheirAreas() throws Exception {
        call(staff, put("/admin/team/" + staff.getId() + "/launch-areas"), "{\"areas\":[\"Gachibowli\",\" Kondapur \",\"Gachibowli\"]}")
                .andExpect(jsonPath("$.data.launchAreas.length()").value(2));
        call(staff, put("/admin/team/" + asha.getId() + "/launch-areas"), "{\"areas\":[\"X\"]}").andExpect(status().isNotFound());
        String body = call(staff, get("/admin/team"), null)
                .andExpect(jsonPath("$.data[0].twoFactorEnabled").value(true))
                .andExpect(jsonPath("$.data[0].launchAreas[0]").value("Gachibowli"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("password").doesNotContain("totpSecret");
        assertThat(audits("staff.areas_set")).isEqualTo(1);
    }

    @Test
    void jennyShowsProvidersAndItsActions() throws Exception {
        call(staff, get("/admin/jenny/providers"), null)
                .andExpect(jsonPath("$.data[0].name").value("jenny"))
                .andExpect(jsonPath("$.data[?(@.name == 'embeddings')].configured").value(false));
        jdbc.update("insert into arena_audit_events (id, created_at, updated_at, actor_user_id, action, target) values (?, now(), now(), ?, 'agent.action.authorized', 'POST /posts')",
                UUID.randomUUID(), asha.getId());
        jdbc.update("insert into arena_audit_events (id, created_at, updated_at, actor_user_id, action, target) values (?, now(), now(), ?, 'user.warned', 'x')",
                UUID.randomUUID(), asha.getId());
        call(staff, get("/admin/jenny/actions"), null)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].target").value("POST /posts"));
    }

    private long audits(String action) {
        em.flush();
        return jdbc.queryForObject("select count(*) from arena_audit_events where action = ?", Long.class, action);
    }

    private User talent(String name) {
        User u = users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash(passwords.encode("correct-horse"))
                .name(name).role(Role.TALENT).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
        profiles.save(CandidateProfile.builder().user(u).name(name).avatarEmoji("*").title("Designer").industry(Industry.DESIGN)
                .location("Hyderabad").remote(false).experienceYears(3).consent(new ConsentSettings(false, true)).build());
        return u;
    }

    private User user(Role role, String name) {
        return users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name(name)
                .role(role).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
    }

    private String token(User u) {
        return tokens.generateToken(u.getId(), u.getEmail(), u.getName(), u.getRole());
    }

    private ResultActions call(User as, MockHttpServletRequestBuilder request, String body) throws Exception {
        return call(token(as), request, body);
    }

    private ResultActions call(String token, MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }

    private String id(ResultActions result) throws Exception {
        JsonNode node = json.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return node.path("data").path("id").asText();
    }
}
