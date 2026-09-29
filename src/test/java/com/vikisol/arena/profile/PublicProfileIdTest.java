package com.vikisol.arena.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.career.entity.CareerEnums;
import com.vikisol.arena.career.entity.CareerProfile;
import com.vikisol.arena.career.repository.CareerProfileRepository;
import com.vikisol.arena.enterprise.entity.CompanySize;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Talent Universe search hands out the CandidateProfile id, while GET /profile/{id} was keyed only
 * on the User id - so every "view profile" link from search 404'd. Both ids must now open the same
 * public profile, and the response id is always the user id.
 */
@AutoConfigureMockMvc
class PublicProfileIdTest extends EmbeddedPostgresAppTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired CandidateProfileRepository profiles;
    @Autowired EnterpriseProfileRepository enterprises;
    @Autowired CareerProfileRepository careers;
    @MockBean TokenDenylistService denylist;

    @BeforeEach
    void signedInTokensAreNotRevoked() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
    }

    @Test
    void aTalentUniverseResultLinksToTheCandidatesPublicProfile() throws Exception {
        User talent = user(Role.TALENT, "Asha Rao");
        CandidateProfile profile = profile(talent, "Zebra-unique platform engineer");
        // Talent search lists only published career profiles (ARENA-APP-FLOW §8).
        careers.save(CareerProfile.builder().user(talent).intent(CareerEnums.Intent.FIND_JOB).publishedAt(java.time.Instant.now()).build());
        User recruiter = user(Role.COMPANY_ADMIN, "Recruiter");
        enterprises.save(EnterpriseProfile.builder().user(recruiter).companyName("Acme").logoEmoji("A")
                .industry(Industry.ENGINEERING).size(CompanySize.S_11_50).build());

        String body = mvc.perform(get("/enterprise/talent/search").param("text", "zebra-unique")
                        .header("Authorization", "Bearer " + token(recruiter)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode results = json.readTree(body).path("data").path("content");
        assertThat(results).hasSize(1);
        String linkedId = results.get(0).path("candidate").path("id").asText();
        assertThat(linkedId).isEqualTo(profile.getId().toString());
        assertThat(linkedId).isNotEqualTo(talent.getId().toString());

        // The link the frontend builds from that search result, opened logged-out.
        mvc.perform(get("/profile/" + linkedId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(talent.getId().toString()))
                .andExpect(jsonPath("$.data.name").value("Asha Rao"));
    }

    @Test
    void theUserIdStillOpensTheSameProfile() throws Exception {
        User talent = user(Role.TALENT, "Ravi Kumar");
        CandidateProfile profile = profile(talent, "Designer");

        mvc.perform(get("/profile/" + talent.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(talent.getId().toString()));
        mvc.perform(get("/profile/" + profile.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(talent.getId().toString()));
    }

    @Test
    void anUnknownIdIsStillNotFound() throws Exception {
        mvc.perform(get("/profile/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    private User user(Role role, String name) {
        return users.save(User.builder()
                .email(UUID.randomUUID() + "@test.local")
                .passwordHash("x")
                .name(name)
                .role(role)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .build());
    }

    private CandidateProfile profile(User user, String title) {
        return profiles.save(CandidateProfile.builder()
                .user(user).name(user.getName()).avatarEmoji("*").title(title)
                .industry(Industry.ENGINEERING).location("Hyderabad").remote(false)
                .consent(new ConsentSettings(false, true))
                .build());
    }

    private String token(User user) {
        return tokens.generateToken(user.getId(), user.getEmail(), user.getName(), user.getRole());
    }
}
