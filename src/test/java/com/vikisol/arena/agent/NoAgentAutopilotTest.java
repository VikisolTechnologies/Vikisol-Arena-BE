package com.vikisol.arena.agent;

import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.profile.entity.AutonomyLevel;
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

import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DECISIONS.md, 30 Sep 2026: Jenny always prepares and the person approves each action. There is
 * no agent_autopilot flag and no autonomy level that lets Jenny act on its own (V42).
 */
@AutoConfigureMockMvc
class NoAgentAutopilotTest extends EmbeddedPostgresAppTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired CandidateProfileRepository profiles;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @MockBean TokenDenylistService denylist;

    private User staff, asha;

    @BeforeEach
    void setUp() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        staff = users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Staff")
                .role(Role.PLATFORM_ADMIN).totpEnabled(true).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
        asha = users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Asha")
                .role(Role.TALENT).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
        profiles.save(CandidateProfile.builder().user(asha).name("Asha").avatarEmoji("*").title("Designer").industry(Industry.DESIGN)
                .location("Hyderabad").remote(false).experienceYears(3).consent(new ConsentSettings(false, true)).build());
    }

    @Test
    void noAutopilotFlagExistsOrCanBeCreated() throws Exception {
        assertThat(jdbc.queryForObject("select count(*) from arena_feature_flags where lower(key) like '%autopilot%'", Long.class))
                .isZero();
        call(staff, post("/admin/flags"), "{\"key\":\"agent_autopilot\",\"label\":\"Agent autopilot\",\"enabled\":false}")
                .andExpect(status().isBadRequest());
        call(staff, post("/admin/flags"), "{\"key\":\"Agent_Autopilot\",\"label\":\"Agent autopilot\",\"enabled\":false}")
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from arena_feature_flags where lower(key) like '%autopilot%'", Long.class))
                .isZero();
    }

    @Test
    void noAutonomyLevelLetsJennyActWithoutApproval() throws Exception {
        assertThat(Arrays.stream(AutonomyLevel.values()).map(Enum::name)).containsExactly("MANUAL", "SUPERVISED");
        call(asha, put("/profile/me/autonomy"), "{\"autonomy\":\"autopilot\"}").andExpect(status().isBadRequest());
        call(asha, put("/profile/me/autonomy"), "{\"autonomy\":\"supervised\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.autonomy").value("supervised"));
        // The database refuses the value too.
        em.flush();
        assertThatThrownBy(() -> jdbc.update("update arena_candidate_profiles set autonomy = 'AUTOPILOT' where user_id = ?", asha.getId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private ResultActions call(User as, MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header("Authorization", "Bearer " + tokens.generateToken(as.getId(), as.getEmail(), as.getName(), as.getRole()));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }
}
