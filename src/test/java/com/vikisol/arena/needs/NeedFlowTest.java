package com.vikisol.arena.needs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.entity.PostIntentType;
import com.vikisol.arena.posts.entity.PostVisibility;
import com.vikisol.arena.posts.repository.PostRepository;
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

/** Needs & offers G14-G17: details, responses, accept into a private chat, two-sided completion, outcomes. */
@AutoConfigureMockMvc
class NeedFlowTest extends EmbeddedPostgresAppTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired PostRepository posts;
    @MockBean TokenDenylistService denylist;

    private User owner, helper, other;

    @BeforeEach
    void people() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        owner = user("Owner");
        helper = user("Helper");
        other = user("Other");
    }

    @Test
    void ownerSetsCategoryAndEveryoneCanRead() throws Exception {
        Post need = newPost(PostIntentType.ASK, false);
        call(owner, put("/needs/" + need.getId() + "/details"), "{\"category\":\"moving\",\"preferredTime\":\"Saturday morning\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.category").value("moving"));
        call(owner, put("/needs/" + need.getId() + "/details"), "{\"category\":\"dating\"}").andExpect(status().isBadRequest());
        call(helper, put("/needs/" + need.getId() + "/details"), "{\"category\":\"moving\"}").andExpect(status().isForbidden());
        mvc.perform(get("/needs/" + need.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("need"))
                .andExpect(jsonPath("$.data.preferredTime").value("Saturday morning"))
                .andExpect(jsonPath("$.data.viewer").doesNotExist());
        mvc.perform(get("/needs/categories")).andExpect(jsonPath("$.data[0]").value("moving"));
    }

    @Test
    void responsesAreSeenByTheOwnerAndEachResponderOnlyTheirOwn() throws Exception {
        Post need = newPost(PostIntentType.ASK, false);
        call(owner, post("/needs/" + need.getId() + "/responses"), "{\"message\":\"x\"}").andExpect(status().isBadRequest());
        call(helper, post("/needs/" + need.getId() + "/responses"), "{\"message\":\"I have a van this weekend\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("pending"));
        call(helper, post("/needs/" + need.getId() + "/responses"), "{\"message\":\"again\"}").andExpect(status().isBadRequest());
        call(other, post("/needs/" + need.getId() + "/responses"), "{\"message\":\"I can lift\"}").andExpect(status().isOk());

        call(owner, get("/needs/" + need.getId() + "/responses"), null)
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].message").value("I have a van this weekend"));
        call(other, get("/needs/" + need.getId() + "/responses"), null)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].userId").value(other.getId().toString()));
        mvc.perform(get("/needs/" + need.getId())).andExpect(jsonPath("$.data.responseCount").value(2));
    }

    @Test
    void anonymousNeedsDontTakeResponsesThatWouldRevealTheAuthor() throws Exception {
        Post need = newPost(PostIntentType.ASK, true);
        call(helper, post("/needs/" + need.getId() + "/responses"), "{\"message\":\"I can help\"}").andExpect(status().isBadRequest());
    }

    @Test
    void acceptOpensAPrivateChatAndBothSidesMustConfirm() throws Exception {
        Post need = newPost(PostIntentType.ASK, false);
        call(owner, put("/needs/" + need.getId() + "/details"), "{\"category\":\"moving\"}");
        String rid = body(call(helper, post("/needs/" + need.getId() + "/responses"), "{\"message\":\"I have a van\"}"))
                .path("data").path("id").asText();
        String base = "/needs/" + need.getId() + "/responses/" + rid;

        call(helper, post(base + "/confirm"), null).andExpect(status().isBadRequest()); // not accepted yet
        call(other, put(base + "/accept"), null).andExpect(status().isForbidden());
        String chat = body(call(owner, put(base + "/accept"), null).andExpect(status().isOk()))
                .path("data").path("conversationId").asText();
        assertThat(chat).isNotBlank();
        call(helper, get("/messages/conversations"), null).andExpect(jsonPath("$.data[0].id").value(chat));

        call(other, post(base + "/confirm"), null).andExpect(status().isForbidden());
        call(owner, post(base + "/confirm"), "{\"note\":\"Thank you so much!\"}")
                .andExpect(jsonPath("$.data.completion.ownerConfirmedAt").exists())
                .andExpect(jsonPath("$.data.completion.completedAt").doesNotExist());
        mvc.perform(get("/needs/outcomes/" + helper.getId())).andExpect(jsonPath("$.data.length()").value(0));

        call(helper, post(base + "/confirm"), null)
                .andExpect(jsonPath("$.data.completion.completedAt").exists())
                .andExpect(jsonPath("$.data.completion.ownerNote").value("Thank you so much!"));
        call(helper, post(base + "/confirm"), null).andExpect(status().isBadRequest());
        call(helper, delete("/needs/" + need.getId() + "/responses/me"), null).andExpect(status().isBadRequest());
        mvc.perform(get("/needs/" + need.getId())).andExpect(jsonPath("$.data.status").value("closed"));

        // Public outcomes: what and when, never with whom.
        mvc.perform(get("/needs/outcomes/" + helper.getId()))
                .andExpect(jsonPath("$.data[0].role").value("gave"))
                .andExpect(jsonPath("$.data[0].title").isNotEmpty())
                .andExpect(jsonPath("$.data[0].category").value("moving"))
                .andExpect(jsonPath("$.data[0].userId").doesNotExist());
        mvc.perform(get("/needs/outcomes/" + owner.getId())).andExpect(jsonPath("$.data[0].role").value("received"));
        // A bystander never sees the private parts.
        call(other, get("/needs/" + need.getId() + "/responses"), null).andExpect(jsonPath("$.data.length()").value(0));
        call(helper, get("/needs/responses/mine"), null)
                .andExpect(jsonPath("$.data[0].response.completion.completedAt").exists());
    }

    @Test
    void onAnOfferTheOwnerIsTheOneWhoGave() throws Exception {
        Post offer = newPost(PostIntentType.OFFER, false);
        String rid = body(call(helper, post("/needs/" + offer.getId() + "/responses"), "{\"message\":\"I'd love a lesson\"}"))
                .path("data").path("id").asText();
        String base = "/needs/" + offer.getId() + "/responses/" + rid;
        call(owner, put(base + "/accept"), null).andExpect(status().isOk());
        call(owner, post(base + "/confirm"), null);
        call(helper, post(base + "/confirm"), null);
        mvc.perform(get("/needs/outcomes/" + owner.getId())).andExpect(jsonPath("$.data[0].role").value("gave"));
        mvc.perform(get("/needs/" + offer.getId())).andExpect(jsonPath("$.data.status").value("open")); // an offer can serve many
    }

    // ARCHITECT-REVIEW-BE-1 SHOULD-FIX: GET /needs/{id} had no audience, block or paused filter -
    // post detail's own rules now apply here too.
    @Test
    void getAppliesAudienceBlockAndPausedFiltering() throws Exception {
        Post paused = newPost(PostIntentType.ASK, false);
        paused.setStatus(com.vikisol.arena.posts.entity.PostStatus.PAUSED);
        posts.save(paused);
        mvc.perform(get("/needs/" + paused.getId())).andExpect(status().isNotFound());
        call(owner, get("/needs/" + paused.getId()), null).andExpect(status().isOk()); // the author still sees it

        Post followersOnly = newPost(PostIntentType.ASK, false);
        followersOnly.setAudience(com.vikisol.arena.posts.entity.PostAudience.FOLLOWERS);
        posts.save(followersOnly);
        call(helper, get("/needs/" + followersOnly.getId()), null).andExpect(status().isNotFound());
        call(owner, get("/needs/" + followersOnly.getId()), null).andExpect(status().isOk());

        Post need = newPost(PostIntentType.ASK, false);
        call(helper, post("/blocks/" + owner.getId()), null).andExpect(status().isOk());
        call(helper, get("/needs/" + need.getId()), null).andExpect(status().isNotFound());
        call(other, get("/needs/" + need.getId()), null).andExpect(status().isOk());
    }

    // ARCHITECT-REVIEW-BE-1 SHOULD-FIX: respond -> withdraw -> respond had no cooldown, which let
    // someone re-notify the owner in a loop.
    @Test
    void respondingAgainRightAfterAWithdrawalIsRateLimited() throws Exception {
        Post need = newPost(PostIntentType.ASK, false);
        call(helper, post("/needs/" + need.getId() + "/responses"), "{\"message\":\"I can help\"}").andExpect(status().isOk());
        call(helper, delete("/needs/" + need.getId() + "/responses/me"), null).andExpect(status().isOk());
        call(helper, post("/needs/" + need.getId() + "/responses"), "{\"message\":\"again\"}").andExpect(status().isBadRequest());
    }

    @Test
    void declineAndNonNeeds() throws Exception {
        Post need = newPost(PostIntentType.ASK, false);
        String rid = body(call(helper, post("/needs/" + need.getId() + "/responses"), "{\"message\":\"Me\"}")).path("data").path("id").asText();
        call(owner, put("/needs/" + need.getId() + "/responses/" + rid + "/decline"), null)
                .andExpect(jsonPath("$.data.status").value("declined"));
        call(helper, post("/needs/" + need.getId() + "/responses"), "{\"message\":\"Please?\"}").andExpect(status().isBadRequest());
        Post activity = newPost(PostIntentType.ACTIVITY, false);
        mvc.perform(get("/needs/" + activity.getId())).andExpect(status().isNotFound());
    }

    private Post newPost(PostIntentType type, boolean anonymous) {
        return posts.save(Post.builder().authorUser(owner).intentType(type).body("Help moving a sofa")
                .visibility(PostVisibility.PUBLIC).anonymous(anonymous).build());
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
                .role(Role.TALENT).dateOfBirth(LocalDate.of(1992, 5, 1)).build());
    }
}
