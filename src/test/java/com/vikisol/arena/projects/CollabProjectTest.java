package com.vikisol.arena.projects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.schema.EmbeddedPostgresAppTest;
import com.vikisol.arena.security.jwt.JwtTokenProvider;
import com.vikisol.arena.security.jwt.TokenDenylistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Community projects G29-G31 and the profile stat row G32. */
@AutoConfigureMockMvc
class CollabProjectTest extends EmbeddedPostgresAppTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @MockBean TokenDenylistService denylist;

    private User owner, dev, designer, late;

    @BeforeEach
    void people() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        owner = user();
        dev = user();
        designer = user();
        late = user();
    }

    @Test
    void startAProjectOpenRolesJoinForARoleAndTheTeamFillsUp() throws Exception {
        String id = create(owner, "{\"intentType\":\"collab\",\"title\":\"Neighbourhood library app\",\"body\":\"Lend books to neighbours\",\"visibility\":\"approval\"}");

        call(owner, put("/projects/" + id + "/roles"), "{\"roles\":[{\"title\":\"Male developer\"}]}").andExpect(status().isBadRequest());
        call(dev, put("/projects/" + id + "/roles"), "{\"roles\":[{\"title\":\"Developer\"}]}").andExpect(status().isForbidden());
        JsonNode roles = body(call(owner, put("/projects/" + id + "/roles"),
                "{\"roles\":[{\"title\":\"Android developer\",\"slots\":1},{\"title\":\"Designer\",\"description\":\"Screens and icons\",\"slots\":2}]}")
                .andExpect(status().isOk())).path("data").path("roles");
        String devRole = roles.get(0).path("id").asText();
        String designRole = roles.get(1).path("id").asText();

        call(dev, post("/projects/" + id + "/join"), "{\"message\":\"Keen\"}").andExpect(status().isBadRequest()); // must pick a role
        String joinId = body(call(dev, post("/projects/" + id + "/join"), "{\"roleId\":\"" + devRole + "\",\"message\":\"I build Android apps\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("pending"))).path("data").path("id").asText();
        call(designer, post("/projects/" + id + "/join"), "{\"roleId\":\"" + designRole + "\"}").andExpect(status().isOk());

        call(dev, get("/projects/" + id + "/requests"), null).andExpect(status().isForbidden());
        call(owner, get("/projects/" + id + "/requests"), null)
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].roleTitle").value("Android developer"))
                .andExpect(jsonPath("$.data[0].message").value("I build Android apps"));

        // Approval is the existing post join approval; the team space is the post's room.
        call(owner, put("/posts/" + id + "/joins/" + joinId + "/approve"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("approved"));
        mvc.perform(get("/projects/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0].filled").value(1))
                .andExpect(jsonPath("$.data.team.length()").value(1))
                .andExpect(jsonPath("$.data.team[0].roleTitle").value("Android developer"));
        call(late, post("/projects/" + id + "/join"), "{\"roleId\":\"" + devRole + "\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("That role is already filled"));
        // Roles are fixed once people have asked for them.
        call(owner, put("/projects/" + id + "/roles"), "{\"roles\":[]}").andExpect(status().isBadRequest());

        mvc.perform(get("/projects/of/" + dev.getId())).andExpect(jsonPath("$.data[0].role").value("Android developer"));
        mvc.perform(get("/projects/of/" + owner.getId())).andExpect(jsonPath("$.data[0].role").value("owner"));
        mvc.perform(get("/projects/of/" + designer.getId())).andExpect(jsonPath("$.data.length()").value(0)); // still pending
    }

    @Test
    void anonymousProjectsAndOtherPostsAreNotProjects() throws Exception {
        call(owner, post("/posts"), "{\"intentType\":\"collab\",\"body\":\"Secret project\",\"anonymous\":true}")
                .andExpect(status().isBadRequest());
        String ask = create(owner, "{\"intentType\":\"ask\",\"body\":\"Need a ladder\"}");
        mvc.perform(get("/projects/" + ask)).andExpect(status().isNotFound());
    }

    @Test
    void profileStatsAreRealCounts() throws Exception {
        String starts = Instant.now().plus(2, ChronoUnit.DAYS).toString();
        String run = create(owner, "{\"intentType\":\"activity\",\"body\":\"Sunday run\",\"startsAt\":\"" + starts + "\"}");
        call(dev, post("/posts/" + run + "/joins"), null).andExpect(status().isOk());

        String need = create(owner, "{\"intentType\":\"ask\",\"body\":\"Help moving\"}");
        String rid = body(call(dev, post("/needs/" + need + "/responses"), "{\"message\":\"I can\"}")).path("data").path("id").asText();
        call(owner, put("/needs/" + need + "/responses/" + rid + "/accept"), null);
        call(owner, post("/needs/" + need + "/responses/" + rid + "/confirm"), null);
        call(dev, post("/needs/" + need + "/responses/" + rid + "/confirm"), null);

        String project = create(owner, "{\"intentType\":\"collab\",\"body\":\"Library app\"}");
        call(dev, post("/projects/" + project + "/join"), "{}").andExpect(status().isOk()); // no roles, open project: straight in

        mvc.perform(get("/profile/" + owner.getId() + "/stats"))
                .andExpect(jsonPath("$.data.hosted").value(1))
                .andExpect(jsonPath("$.data.joined").value(0))
                .andExpect(jsonPath("$.data.helped").value(0))
                .andExpect(jsonPath("$.data.projects").value(1));
        mvc.perform(get("/profile/" + dev.getId() + "/stats"))
                .andExpect(jsonPath("$.data.hosted").value(0))
                .andExpect(jsonPath("$.data.joined").value(1))
                .andExpect(jsonPath("$.data.helped").value(1))
                .andExpect(jsonPath("$.data.projects").value(1));
        mvc.perform(get("/profile/" + UUID.randomUUID() + "/stats")).andExpect(status().isNotFound());
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

    private User user() {
        return users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Person")
                .role(Role.TALENT).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
    }
}
