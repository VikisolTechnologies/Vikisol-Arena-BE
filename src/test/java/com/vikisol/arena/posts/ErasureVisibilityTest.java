package com.vikisol.arena.posts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.notifications.entity.Notification;
import com.vikisol.arena.notifications.repository.NotificationRepository;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Erasing an account retires what they still had open, and banned or erased authors leave public lists. */
@AutoConfigureMockMvc
class ErasureVisibilityTest extends EmbeddedPostgresAppTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired CandidateProfileRepository profiles;
    @Autowired NotificationRepository notifications;
    @MockBean TokenDenylistService denylist;
    @MockBean com.vikisol.arena.security.jwt.RefreshTokenService refreshTokens;

    private User host;
    private User joiner;

    @BeforeEach
    void people() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        host = talent("Host");
        joiner = talent("Asha");
    }

    @Test
    void erasingCancelsUpcomingWorkAndKeepsFinishedHistory() throws Exception {
        String soon = create(host, activity("Morning walk", Instant.now().plus(2, ChronoUnit.DAYS)));
        call(joiner, post("/posts/" + soon + "/joins"), null).andExpect(status().isOk());
        String need = create(host, "{\"intentType\":\"ask\",\"title\":\"Borrow a ladder\",\"body\":\"Need one this week\",\"lat\":17.44,\"lng\":78.35}");
        String past = create(host, activity("Old match", Instant.now().minus(3, ChronoUnit.DAYS)));

        call(host, delete("/profile/me"), null).andExpect(status().isOk());

        call(joiner, get("/posts/" + soon), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("cancelled"));
        call(joiner, get("/posts/" + need), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("closed"));
        call(joiner, get("/posts/" + past), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("open"))
                .andExpect(jsonPath("$.data.authorName").value("a former member"));

        Notification told = notifications.findByUserIdOrderByCreatedAtDesc(joiner.getId(), PageRequest.of(0, 5))
                .getContent().stream().filter(n -> "Activity cancelled".equals(n.getTitle())).findFirst().orElseThrow();
        assertThat(told.getBody()).contains("no longer on Arena");

        String nearby = body(call(joiner, get("/posts/nearby?lat=17.44&lng=78.35&radiusKm=5"), null).andExpect(status().isOk()))
                .path("data").toString();
        assertThat(nearby).doesNotContain("Morning walk").doesNotContain("Borrow a ladder").doesNotContain("Old match");
    }

    @Test
    void aBannedAuthorsOpenPostIsLeftOffTheMap() throws Exception {
        User banned = talent("Banned host");
        String hidden = create(banned, activity("Hidden cricket", Instant.now().plus(1, ChronoUnit.DAYS)));
        String shown = create(joiner, activity("Open cricket", Instant.now().plus(1, ChronoUnit.DAYS)));
        banned.setBannedAt(Instant.now());
        users.save(banned);

        String nearby = body(call(host, get("/posts/nearby?lat=17.44&lng=78.35&radiusKm=5"), null).andExpect(status().isOk()))
                .path("data").toString();
        assertThat(nearby).contains("Open cricket").doesNotContain("Hidden cricket");
        call(host, get("/posts/" + hidden), null).andExpect(jsonPath("$.data.status").value("open"));
    }

    private static String activity(String title, Instant startsAt) {
        return "{\"intentType\":\"activity\",\"title\":\"" + title + "\",\"body\":\"" + title + "\",\"startsAt\":\""
                + startsAt + "\",\"lat\":17.44,\"lng\":78.35}";
    }

    private String create(User as, String body) throws Exception {
        return body(call(as, post("/posts"), body).andExpect(status().isOk())).path("data").path("id").asText();
    }

    private ResultActions call(User as, MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header("Authorization", "Bearer " + tokens.generateToken(as.getId(), as.getEmail(), as.getName(), as.getRole()));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private User talent(String name) {
        User u = users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name(name)
                .role(Role.TALENT).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
        profiles.save(CandidateProfile.builder().user(u).name(name).avatarEmoji("*").title("Organiser").industry(Industry.DESIGN)
                .location("Hyderabad").remote(false).experienceYears(3).consent(new ConsentSettings(false, true)).build());
        return u;
    }
}
