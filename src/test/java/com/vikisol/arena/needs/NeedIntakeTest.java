package com.vikisol.arena.needs;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Needs & offers per ARENA-APP-FLOW §4 and FE-API-GAPS rows 27 and 38. */
@AutoConfigureMockMvc
class NeedIntakeTest extends EmbeddedPostgresAppTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @MockBean TokenDenylistService denylist;

    private User owner, asha, ravi, meera;

    @BeforeEach
    void people() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        owner = user("Owner");
        asha = user("Asha");
        ravi = user("Ravi");
        meera = user("Meera");
    }

    @Test
    void aNeedAndItsIntakeAreCreatedInOneCall() throws Exception {
        String id = create("{\"intentType\":\"ask\",\"title\":\"Help move a sofa\",\"body\":\"3-seater, 3rd floor with lift\","
                + "\"need\":{\"category\":\"moving\",\"urgency\":\"week\",\"helpType\":\"costs\","
                + "\"answers\":{\"items\":\"3-seater sofa\",\"helpers\":2,\"vehicle\":false}}}");
        mvc.perform(get("/needs/" + id))
                .andExpect(jsonPath("$.data.category").value("moving"))
                .andExpect(jsonPath("$.data.urgency").value("week"))
                .andExpect(jsonPath("$.data.helpType").value("costs"))
                .andExpect(jsonPath("$.data.answers.helpers").value(2))
                .andExpect(jsonPath("$.data.offer").doesNotExist());
        // The frontend's hyphenated ids, and the earlier names still accepted.
        call(owner, put("/needs/" + id + "/details"), "{\"category\":\"pet-care\"}").andExpect(jsonPath("$.data.category").value("pet-care"));
        call(owner, put("/needs/" + id + "/details"), "{\"category\":\"tech_help\"}").andExpect(jsonPath("$.data.category").value("tech"));
        // Offer-only fields on a need are refused, and so is a bad need on create. (The create's
        // rollback can't be observed here: each test runs inside one outer test transaction.)
        call(owner, put("/needs/" + id + "/details"), "{\"category\":\"moving\",\"limit\":\"once-a-week\"}").andExpect(status().isBadRequest());
        call(owner, post("/posts"), "{\"intentType\":\"ask\",\"body\":\"x\",\"need\":{\"category\":\"moving\",\"urgency\":\"someday\"}}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void anOffersLimitIsEnforcedOnAccept() throws Exception {
        String id = create("{\"intentType\":\"offer\",\"title\":\"Maths tutoring\",\"body\":\"Class 8-10 maths\","
                + "\"need\":{\"category\":\"tutoring\",\"helpType\":\"free\",\"days\":[\"weekends\",\"evenings\"],"
                + "\"limit\":\"once-a-week\",\"proofUrl\":\"https://example.org/me\"}}");
        mvc.perform(get("/needs/" + id))
                .andExpect(jsonPath("$.data.offer.days[0]").value("weekends"))
                .andExpect(jsonPath("$.data.offer.limit").value("once-a-week"))
                .andExpect(jsonPath("$.data.offer.proofUrl").value("https://example.org/me"))
                .andExpect(jsonPath("$.data.offer.limitReached").value(false));
        call(owner, put("/needs/" + id + "/details"), "{\"category\":\"tutoring\",\"helpType\":\"costs\"}").andExpect(status().isBadRequest());
        call(owner, put("/needs/" + id + "/details"), "{\"category\":\"tutoring\",\"proofUrl\":\"javascript:alert(1)\"}").andExpect(status().isBadRequest());

        String first = respond(asha, id);
        String second = respond(ravi, id);
        call(owner, put("/needs/" + id + "/responses/" + first + "/accept"), null).andExpect(status().isOk());
        mvc.perform(get("/needs/" + id)).andExpect(jsonPath("$.data.offer.limitReached").value(true));
        call(owner, put("/needs/" + id + "/responses/" + second + "/accept"), null).andExpect(status().isBadRequest());
        call(owner, put("/needs/" + id + "/details"), "{\"category\":\"tutoring\",\"limit\":\"twice-a-week\"}").andExpect(status().isOk());
        call(owner, put("/needs/" + id + "/responses/" + second + "/accept"), null).andExpect(status().isOk());
    }

    @Test
    void feedNeedCardsCountOffersAndShowAFewFaces() throws Exception {
        String id = create("{\"intentType\":\"ask\",\"body\":\"Need a drill for an afternoon\",\"need\":{\"category\":\"borrow\"}}");
        String declined = respond(asha, id);
        respond(ravi, id);
        respond(meera, id);
        call(owner, put("/needs/" + id + "/responses/" + declined + "/decline"), null).andExpect(status().isOk());
        JsonNode item = null;
        for (JsonNode n : body(call(asha, get("/feed").param("size", "50"), null)).path("data")) {
            if (n.path("id").asText().equals(id)) item = n;
        }
        assertThat(item).isNotNull();
        assertThat(item.path("offerCount").asLong()).isEqualTo(2);
        assertThat(item.path("offerAvatars")).hasSize(2);
        assertThat(item.path("offerAvatars").get(0).path("name").asText()).isEqualTo("Ravi");
    }

    private String respond(User who, String id) throws Exception {
        return body(call(who, post("/needs/" + id + "/responses"), "{\"message\":\"I can help\"}").andExpect(status().isOk()))
                .path("data").path("id").asText();
    }

    private String create(String body) throws Exception {
        return body(call(owner, post("/posts"), body).andExpect(status().isOk())).path("data").path("id").asText();
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
