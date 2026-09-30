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

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Community projects per ARENA-APP-FLOW §7 (FE-API-GAPS row 26): PR1-PR6. */
@AutoConfigureMockMvc
class ProjectFlowDocTest extends EmbeddedPostgresAppTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @MockBean TokenDenylistService denylist;

    private User owner, asha, ravi, outsider;

    @BeforeEach
    void people() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        owner = user("Owner");
        asha = user("Asha");
        ravi = user("Ravi");
        outsider = user("Outsider");
    }

    @Test
    void startApplyPlanAndComplete() throws Exception {
        // PR1-PR2 in one call.
        JsonNode created = body(call(owner, post("/projects"), "{\"title\":\"Lake clean-up map\",\"goal\":\"Map every inlet of the lake\","
                + "\"category\":\"environment\",\"where\":\"both\",\"weeks\":6,"
                + "\"roles\":[{\"title\":\"Designer\",\"count\":2,\"skills\":[\"Figma\"],\"hoursPerWeek\":4},{\"title\":\"Developer\"}]}")
                .andExpect(status().isOk())).path("data");
        String id = created.path("postId").asText();
        String designer = created.path("roles").get(0).path("id").asText();
        mvc.perform(get("/projects/" + id))
                .andExpect(jsonPath("$.data.goal").value("Map every inlet of the lake"))
                .andExpect(jsonPath("$.data.category").value("environment"))
                .andExpect(jsonPath("$.data.where").value("both"))
                .andExpect(jsonPath("$.data.weeks").value(6))
                .andExpect(jsonPath("$.data.roles[0].slots").value(2))
                .andExpect(jsonPath("$.data.roles[0].skills[0]").value("Figma"))
                .andExpect(jsonPath("$.data.roles[0].hoursPerWeek").value(4));
        call(owner, post("/projects"), "{\"title\":\"x\",\"goal\":\"y\",\"category\":\"astrology\"}").andExpect(status().isBadRequest());

        // PR4: apply with a note; the owner accepts.
        String ashaJoin = body(call(asha, post("/projects/" + id + "/applications"), "{\"roleId\":\"" + designer + "\",\"note\":\"I map for fun\"}")
                .andExpect(status().isOk())).path("data").path("id").asText();
        String raviJoin = body(call(ravi, post("/projects/" + id + "/applications"), "{\"roleId\":\"" + designer + "\"}")
                .andExpect(status().isOk())).path("data").path("id").asText();
        call(owner, get("/projects/" + id + "/requests"), null).andExpect(jsonPath("$.data[0].message").value("I map for fun"));
        call(owner, put("/posts/" + id + "/joins/" + ashaJoin + "/approve"), null).andExpect(status().isOk());

        // PR5: the Plan checklist is for the team only.
        call(outsider, get("/projects/" + id + "/milestones"), null).andExpect(status().isForbidden());
        call(ravi, post("/projects/" + id + "/milestones"), "{\"title\":\"Walk the east shore\"}").andExpect(status().isForbidden()); // still pending
        String first = body(call(owner, post("/projects/" + id + "/milestones"), "{\"title\":\"Walk the east shore\"}")
                .andExpect(status().isOk())).path("data").get(0).path("id").asText();
        call(asha, post("/projects/" + id + "/milestones"), "{\"title\":\"Draw the map\"}").andExpect(jsonPath("$.data.length()").value(2));
        call(asha, put("/projects/" + id + "/milestones/" + first), "{\"done\":true}")
                .andExpect(jsonPath("$.data[0].done").value(true))
                .andExpect(jsonPath("$.data[0].doneAt").exists());
        call(asha, delete("/projects/" + id + "/milestones/" + first), null).andExpect(status().isForbidden()); // the owner added it
        call(owner, delete("/projects/" + id + "/milestones/" + first), null).andExpect(jsonPath("$.data.length()").value(1));

        // PR6: complete with contributors from the team only.
        call(asha, post("/projects/" + id + "/complete"), "{\"outcome\":\"Done\"}").andExpect(status().isForbidden());
        call(owner, post("/projects/" + id + "/complete"), "{\"outcome\":\"Mapped 14 inlets\",\"contributorIds\":[\"" + ravi.getId() + "\"]}")
                .andExpect(status().isBadRequest());
        call(owner, post("/projects/" + id + "/complete"), "{\"outcome\":\"Mapped 14 inlets\",\"contributorIds\":[\"" + asha.getId() + "\"]}")
                .andExpect(jsonPath("$.data.status").value("closed"))
                .andExpect(jsonPath("$.data.outcome").value("Mapped 14 inlets"))
                .andExpect(jsonPath("$.data.contributors[0].name").value("Asha"));
        call(owner, post("/projects/" + id + "/complete"), "{\"outcome\":\"Again\"}").andExpect(status().isBadRequest());
        mvc.perform(get("/projects/of/" + asha.getId()))
                .andExpect(jsonPath("$.data[0].outcome").value("Mapped 14 inlets"))
                .andExpect(jsonPath("$.data[0].contributor").value(true))
                .andExpect(jsonPath("$.data[0].role").value("Designer"));
    }

    @Test
    void theOwnerUploadsASignedCover() throws Exception {
        String id = body(call(owner, post("/projects"), "{\"title\":\"Library corner\",\"goal\":\"A free shelf\",\"category\":\"community\"}")
                .andExpect(status().isOk())).path("data").path("postId").asText();
        var file = new org.springframework.mock.web.MockMultipartFile("file", "cover.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0});
        call(asha, multipart("/projects/" + id + "/cover").file(file), null).andExpect(status().isForbidden());
        call(owner, multipart("/projects/" + id + "/cover").file(file), null)
                .andExpect(jsonPath("$.data.coverUrl").value(org.hamcrest.Matchers.containsString("sig=")));
        call(owner, delete("/projects/" + id + "/cover"), null).andExpect(jsonPath("$.data.coverUrl").doesNotExist());
    }

    private ResultActions call(User as, MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header("Authorization", "Bearer " + tokens.generateToken(as.getId(), as.getEmail(), as.getName(), as.getRole()));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private User user(String name) {
        return users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name(name)
                .role(Role.TALENT).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
    }
}
