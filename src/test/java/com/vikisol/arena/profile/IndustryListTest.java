package com.vikisol.arena.profile;

import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.entity.ConsentSettings;
import com.vikisol.arena.profile.entity.Industry;
import com.vikisol.arena.profile.industry.IndustryCatalogue;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FE-API-GAPS row 62: the industry list is open and staff-managed (V44), not a closed five. */
@AutoConfigureMockMvc
class IndustryListTest extends EmbeddedPostgresAppTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired CandidateProfileRepository profiles;
    @Autowired IndustryCatalogue catalogue;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @MockBean TokenDenylistService denylist;

    private User staff, asha, ravi;

    @BeforeEach
    void setUp() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        staff = users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Staff")
                .role(Role.PLATFORM_ADMIN).totpEnabled(true).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
        asha = talent("Asha", Industry.DESIGN);
        ravi = talent("Ravi", Industry.SALES);
    }

    @Test
    void theFiveOriginalIndustriesAreListedWithoutSigningIn() throws Exception {
        mvc.perform(get("/public/industries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].key").value("ENGINEERING"))
                .andExpect(jsonPath("$.data[0].label").value("Engineering"))
                .andExpect(jsonPath("$.data[4].label").value("Logistics"));
    }

    @Test
    void staffAddAnIndustryAndPeopleCanPickIt() throws Exception {
        call(asha, post("/admin/industries"), "{\"label\":\"Education\"}").andExpect(status().isForbidden());

        call(staff, post("/admin/industries"), "{\"label\":\"  Education \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.key").value("EDUCATION"))
                .andExpect(jsonPath("$.data.label").value("Education"))
                .andExpect(jsonPath("$.data.active").value(true));
        call(staff, post("/admin/industries"), "{\"label\":\"education\"}").andExpect(status().isBadRequest());
        call(staff, post("/admin/industries"), "{\"label\":\"\"}").andExpect(status().isBadRequest());
        mvc.perform(get("/public/industries")).andExpect(jsonPath("$.data[*].label", hasItem("Education")));
        call(staff, get("/admin/industries"), null).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(6));

        catalogue.load(); // stands in for the reload that runs when the admin change commits
        details(asha, "Education").andExpect(status().isOk()).andExpect(jsonPath("$.data.industry").value("Education"));
        em.flush();
        assertThat(jdbc.queryForObject("select industry from arena_candidate_profiles where user_id = ?", String.class, asha.getId()))
                .isEqualTo("EDUCATION");
        assertThat(jdbc.queryForObject("select count(*) from arena_audit_events where action = 'industry.added' and target = 'industry:EDUCATION'",
                Long.class)).isEqualTo(1);

        // Unknown values are refused with a 400, not a server error.
        details(ravi, "Astrology").andExpect(status().isBadRequest());
    }

    @Test
    void aRetiredIndustryStaysOnExistingProfilesButCantBeNewlyPicked() throws Exception {
        call(staff, put("/admin/industries/SALES"), "{\"label\":\"Sales & Business Development\",\"active\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
        call(staff, put("/admin/industries/NOPE"), "{\"active\":false}").andExpect(status().isNotFound());
        call(staff, put("/admin/industries/DESIGN"), "{\"label\":\"Engineering\"}").andExpect(status().isBadRequest());
        mvc.perform(get("/public/industries"))
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[*].key", not(hasItem("SALES"))));
        catalogue.load();

        // Ravi is already in Sales: saving his profile again keeps it, under the new label.
        details(ravi, "SALES").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.industry").value("Sales & Business Development"));
        // Asha can't move into it.
        details(asha, "Sales & Business Development").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select metadata from arena_audit_events where action = 'industry.updated' and target = 'industry:SALES'",
                String.class)).contains("active=false");
    }

    @Test
    void theDatabaseOnlyAcceptsListedIndustries() {
        em.flush();
        assertThatThrownBy(() -> jdbc.update("update arena_candidate_profiles set industry = 'ASTROLOGY' where user_id = ?", asha.getId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private User talent(String name, Industry industry) {
        User u = users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name(name)
                .role(Role.TALENT).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
        profiles.save(CandidateProfile.builder().user(u).name(name).avatarEmoji("*").title("Designer").industry(industry)
                .location("Hyderabad").remote(false).experienceYears(3).consent(new ConsentSettings(false, true)).build());
        return u;
    }

    private ResultActions details(User as, String industry) throws Exception {
        return call(as, put("/profile/me/details"), "{\"name\":\"" + as.getName() + "\",\"title\":\"Designer\",\"industry\":\""
                + industry + "\",\"experienceYears\":3,\"rateFloor\":0,\"openTo\":[\"projects\"]}");
    }

    private ResultActions call(User as, MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header("Authorization", "Bearer " + tokens.generateToken(as.getId(), as.getEmail(), as.getName(), as.getRole()));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }
}
