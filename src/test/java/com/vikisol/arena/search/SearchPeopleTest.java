package com.vikisol.arena.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// ARCHITECT-REVIEW-BE-1 SHOULD-FIX (access/privacy): people search excluded blocked people
// already (BlockService.isBlockedEitherDirection) but never excluded a banned account.
@AutoConfigureMockMvc
class SearchPeopleTest extends EmbeddedPostgresAppTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired CandidateProfileRepository profiles;
    @MockBean TokenDenylistService denylist;

    private User viewer;

    @BeforeEach
    void setUp() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        viewer = user("Viewer");
    }

    @Test
    void aBannedPersonNeverAppearsInPeopleSearch() throws Exception {
        User visible = user("Priya Designer");
        profile(visible, "Priya Designer");
        User banned = user("Asha Designer");
        banned.setBannedAt(Instant.now());
        users.save(banned);
        profile(banned, "Asha Designer");

        JsonNode results = body(call(get("/search").param("q", "designer").param("type", "people"))).path("data").path("people");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).path("name").asText()).isEqualTo("Priya Designer");
    }

    private void profile(User owner, String name) {
        profiles.save(CandidateProfile.builder().user(owner).name(name).avatarEmoji("*").title("Designer")
                .industry(Industry.DESIGN).location("Hyderabad").remote(false).consent(new ConsentSettings(false, true)).build());
    }

    private User user(String name) {
        return users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name(name)
                .role(Role.TALENT).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
    }

    private ResultActions call(MockHttpServletRequestBuilder request) throws Exception {
        request.header("Authorization", "Bearer " + tokens.generateToken(viewer.getId(), viewer.getEmail(), viewer.getName(), viewer.getRole()));
        return mvc.perform(request);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
