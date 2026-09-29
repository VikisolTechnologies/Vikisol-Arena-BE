package com.vikisol.arena.activities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikisol.arena.activities.entity.ActivityAttendance;
import com.vikisol.arena.activities.repository.ActivityAttendanceRepository;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.entity.PostIntentType;
import com.vikisol.arena.posts.entity.PostJoinRequest;
import com.vikisol.arena.posts.entity.PostVisibility;
import com.vikisol.arena.posts.repository.PostJoinRequestRepository;
import com.vikisol.arena.posts.repository.PostRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Activities G7-G13: details, cover, host questions, waitlist, check-in, attendance + 72h dispute, private feedback. */
@AutoConfigureMockMvc
class ActivityFlowTest extends EmbeddedPostgresAppTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired PostRepository posts;
    @Autowired PostJoinRequestRepository joins;
    @Autowired ActivityAttendanceRepository attendance;
    @Autowired EntityManager em;
    @MockBean TokenDenylistService denylist;

    private User host, asha, ravi, meera;

    @BeforeEach
    void people() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        host = user("Host");
        asha = user("Asha");
        ravi = user("Ravi");
        meera = user("Meera");
    }

    // --- G7 details, G13 cover ---

    @Test
    void hostSetsTypedDetailsAndCoverEveryoneCanRead() throws Exception {
        Post run = activity(null, PostVisibility.PUBLIC, Instant.now().plus(2, ChronoUnit.DAYS));
        call(host, put("/activities/" + run.getId() + "/details"),
                "{\"category\":\"fitness\",\"subtype\":\"running\",\"level\":\"all\",\"cost\":{\"type\":\"free\"},"
                        + "\"typeAnswers\":{\"distance\":\"5 km\",\"pace\":\"easy\"}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category").value("fitness"))
                .andExpect(jsonPath("$.data.typeAnswers.distance").value("5 km"));
        // A subtype that isn't in the category, and protected-attribute wording, are refused.
        call(host, put("/activities/" + run.getId() + "/details"), "{\"subtype\":\"cricket\"}")
                .andExpect(status().isBadRequest());
        call(host, put("/activities/" + run.getId() + "/details"), "{\"typeAnswers\":{\"pace\":\"Women only\"}}")
                .andExpect(status().isBadRequest());
        call(asha, put("/activities/" + run.getId() + "/details"), "{\"category\":\"sports\",\"subtype\":\"cricket\"}")
                .andExpect(status().isForbidden());

        mvc.perform(multipart("/activities/" + run.getId() + "/cover").file(new MockMultipartFile("file", "c.png", "image/png", PNG))
                        .header("Authorization", bearer(host)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coverUrl").exists());
        mvc.perform(multipart("/activities/" + run.getId() + "/cover").file(new MockMultipartFile("file", "c.png", "image/png", PNG))
                        .header("Authorization", bearer(asha)))
                .andExpect(status().isForbidden());

        // A guest reads the activity page; there is no viewer block.
        mvc.perform(get("/activities/" + run.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.typeAnswers.pace").value("easy"))
                .andExpect(jsonPath("$.data.viewer").doesNotExist());
        mvc.perform(get("/activities/kinds")).andExpect(status().isOk()).andExpect(jsonPath("$.data.sports[0]").value("cricket"));
    }

    // --- G8 host questions ---

    @Test
    void hostQuestionsAreAnsweredToJoinAndSeenOnlyByHostAndAnswerer() throws Exception {
        Post game = activity(null, PostVisibility.PUBLIC, Instant.now().plus(1, ChronoUnit.DAYS));
        call(host, put("/activities/" + game.getId() + "/questions"), "{\"questions\":[{\"text\":\"How old are you?\"}]}")
                .andExpect(status().isBadRequest());
        call(host, put("/activities/" + game.getId() + "/questions"),
                "{\"questions\":[{\"text\":\"a\"},{\"text\":\"b\"},{\"text\":\"c\"},{\"text\":\"d\"}]}")
                .andExpect(status().isBadRequest());
        String qid = body(call(host, put("/activities/" + game.getId() + "/questions"),
                "{\"questions\":[{\"text\":\"Have you played doubles before?\"}]}").andExpect(status().isOk()))
                .path("data").path("questions").get(0).path("id").asText();

        // The plain join (the one Jenny uses) can't carry answers, so it's refused here.
        call(asha, post("/posts/" + game.getId() + "/joins"), null).andExpect(status().isBadRequest());
        call(asha, post("/activities/" + game.getId() + "/join"), "{\"answers\":[]}").andExpect(status().isBadRequest());
        call(asha, post("/activities/" + game.getId() + "/join"),
                "{\"answers\":[{\"questionId\":\"" + qid + "\",\"answer\":\"Yes, weekly\"}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("approved"));

        call(host, get("/activities/" + game.getId() + "/answers/" + asha.getId()), null)
                .andExpect(jsonPath("$.data[0].answer").value("Yes, weekly"));
        call(asha, get("/activities/" + game.getId() + "/answers/" + asha.getId()), null).andExpect(status().isOk());
        call(ravi, get("/activities/" + game.getId() + "/answers/" + asha.getId()), null).andExpect(status().isForbidden());
        // Once someone answered, the questions are fixed.
        call(host, put("/activities/" + game.getId() + "/questions"), "{\"questions\":[]}").andExpect(status().isBadRequest());
    }

    // --- G9 waitlist ---

    @Test
    void aFreedSpotGoesToTheFirstPersonOnTheWaitlist() throws Exception {
        Post walk = activity(1, PostVisibility.PUBLIC, Instant.now().plus(1, ChronoUnit.DAYS));
        call(ravi, post("/activities/" + walk.getId() + "/waitlist"), null).andExpect(status().isBadRequest()); // not full yet
        call(asha, post("/posts/" + walk.getId() + "/joins"), null).andExpect(status().isOk());
        call(ravi, post("/posts/" + walk.getId() + "/joins"), null).andExpect(status().isBadRequest()); // full

        call(ravi, post("/activities/" + walk.getId() + "/waitlist"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.viewer.waitlistPosition").value(1));
        call(meera, post("/activities/" + walk.getId() + "/waitlist"), null)
                .andExpect(jsonPath("$.data.viewer.waitlistPosition").value(2))
                .andExpect(jsonPath("$.data.waitlistCount").value(2));
        call(meera, post("/activities/" + walk.getId() + "/waitlist"), null).andExpect(status().isBadRequest());
        call(host, get("/activities/" + walk.getId() + "/waitlist"), null)
                .andExpect(jsonPath("$.data[0].userId").value(ravi.getId().toString()));
        call(asha, get("/activities/" + walk.getId() + "/waitlist"), null).andExpect(status().isForbidden());

        // Asha leaves: Ravi is in, Meera moves up.
        call(asha, delete("/posts/" + walk.getId() + "/joins/me"), null).andExpect(status().isOk());
        call(ravi, get("/activities/" + walk.getId()), null)
                .andExpect(jsonPath("$.data.viewer.joinStatus").value("approved"))
                .andExpect(jsonPath("$.data.viewer.waitlistPosition").doesNotExist());
        call(meera, get("/activities/" + walk.getId()), null).andExpect(jsonPath("$.data.viewer.waitlistPosition").value(1));
        call(meera, delete("/activities/" + walk.getId() + "/waitlist"), null)
                .andExpect(jsonPath("$.data.waitlistCount").value(0));
    }

    @Test
    void anApprovalActivityTurnsTheNextInLineIntoARequest() throws Exception {
        Post dinner = activity(1, PostVisibility.APPROVAL, Instant.now().plus(1, ChronoUnit.DAYS));
        String joinId = body(call(asha, post("/posts/" + dinner.getId() + "/joins"), null)).path("data").path("id").asText();
        call(host, put("/posts/" + dinner.getId() + "/joins/" + joinId + "/approve"), null).andExpect(status().isOk());
        call(ravi, post("/activities/" + dinner.getId() + "/waitlist"), null).andExpect(status().isOk());
        call(asha, delete("/posts/" + dinner.getId() + "/joins/me"), null).andExpect(status().isOk());
        call(ravi, get("/activities/" + dinner.getId()), null).andExpect(jsonPath("$.data.viewer.joinStatus").value("pending"));
    }

    // --- G10 check-in ---

    @Test
    void checkInOpensAnHourBeforeTheStartForPeopleWhoJoined() throws Exception {
        Post soon = activity(null, PostVisibility.PUBLIC, Instant.now().plus(30, ChronoUnit.MINUTES));
        Post later = activity(null, PostVisibility.PUBLIC, Instant.now().plus(3, ChronoUnit.HOURS));
        call(asha, post("/posts/" + soon.getId() + "/joins"), null);
        call(asha, post("/posts/" + later.getId() + "/joins"), null);

        call(asha, post("/activities/" + soon.getId() + "/check-in"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.viewer.checkedInAt").exists());
        call(asha, post("/activities/" + later.getId() + "/check-in"), null).andExpect(status().isBadRequest());
        call(ravi, post("/activities/" + soon.getId() + "/check-in"), null).andExpect(status().isBadRequest());
    }

    // --- G11 attendance + 72h dispute ---

    @Test
    void aNoShowCanBeDisputedFor72HoursAndNeverCountsWhileOpen() throws Exception {
        Post run = activity(null, PostVisibility.PUBLIC, Instant.now().plus(1, ChronoUnit.HOURS));
        String joinId = body(call(asha, post("/posts/" + run.getId() + "/joins"), null)).path("data").path("id").asText();
        started(run);

        call(asha, put("/posts/" + run.getId() + "/joins/" + joinId + "/outcome"), "{\"outcome\":\"no_show\"}")
                .andExpect(status().isForbidden());
        call(host, put("/posts/" + run.getId() + "/joins/" + joinId + "/outcome"), "{\"outcome\":\"no_show\"}")
                .andExpect(status().isOk());
        // Inside the window the no-show doesn't lower the public join count yet.
        assertThat(publicJoinCount(asha)).isEqualTo(1);
        call(asha, get("/activities/" + run.getId()), null).andExpect(jsonPath("$.data.viewer.disputeOpenUntil").exists());

        // Attendance is private: only the host reads the sheet.
        call(ravi, get("/activities/" + run.getId() + "/attendance"), null).andExpect(status().isForbidden());
        call(asha, get("/activities/" + run.getId() + "/attendance"), null).andExpect(status().isForbidden());

        call(asha, post("/activities/" + run.getId() + "/attendance/dispute"), "{\"reason\":\"I was at the lake gate\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.viewer.disputeStatus").value("open"));
        call(asha, post("/activities/" + run.getId() + "/attendance/dispute"), "{\"reason\":\"again\"}")
                .andExpect(status().isBadRequest());
        age(joinId, 100);
        assertThat(publicJoinCount(asha)).as("an open dispute keeps counting").isEqualTo(1);

        call(host, get("/activities/" + run.getId() + "/attendance"), null)
                .andExpect(jsonPath("$.data[0].disputeStatus").value("open"))
                .andExpect(jsonPath("$.data[0].disputeReason").value("I was at the lake gate"));
        call(host, put("/activities/" + run.getId() + "/attendance/" + joinId + "/accept-dispute"), null)
                .andExpect(jsonPath("$.data[0].outcome").value("attended"))
                .andExpect(jsonPath("$.data[0].disputeStatus").value("accepted"));
        call(host, put("/posts/" + run.getId() + "/joins/" + joinId + "/outcome"), "{\"outcome\":\"no_show\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void anUndisputedNoShowBecomesFinalAfter72Hours() throws Exception {
        Post run = activity(null, PostVisibility.PUBLIC, Instant.now().plus(1, ChronoUnit.HOURS));
        String joinId = body(call(ravi, post("/posts/" + run.getId() + "/joins"), null)).path("data").path("id").asText();
        started(run);
        call(host, put("/posts/" + run.getId() + "/joins/" + joinId + "/outcome"), "{\"outcome\":\"no_show\"}");
        age(joinId, 73);
        assertThat(publicJoinCount(ravi)).isZero();
        call(ravi, post("/activities/" + run.getId() + "/attendance/dispute"), "{\"reason\":\"late\"}")
                .andExpect(status().isBadRequest());
    }

    // --- G12 private feedback ---

    @Test
    void feedbackIsPrivateBetweenHostAndParticipant() throws Exception {
        Post meetup = activity(null, PostVisibility.PUBLIC, Instant.now().plus(1, ChronoUnit.HOURS));
        call(asha, post("/posts/" + meetup.getId() + "/joins"), null);
        call(ravi, post("/posts/" + meetup.getId() + "/joins"), null);
        String toAsha = "{\"toUserId\":\"" + asha.getId() + "\",\"joinAgain\":true,\"note\":\"Great energy, thanks for coming!\"}";
        call(host, post("/activities/" + meetup.getId() + "/feedback"), toAsha).andExpect(status().isBadRequest()); // not started
        started(meetup);

        call(host, post("/activities/" + meetup.getId() + "/feedback"), toAsha).andExpect(status().isOk());
        call(asha, post("/activities/" + meetup.getId() + "/feedback"), "{\"joinAgain\":true}").andExpect(status().isOk()); // to the host by default
        call(ravi, post("/activities/" + meetup.getId() + "/feedback"), toAsha).andExpect(status().isForbidden());
        call(meera, post("/activities/" + meetup.getId() + "/feedback"),
                "{\"joinAgain\":false}").andExpect(status().isForbidden());

        call(asha, get("/activities/feedback/received"), null)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].joinAgain").value(true))
                .andExpect(jsonPath("$.data[0].note").value("Great energy, thanks for coming!"));
        call(ravi, get("/activities/feedback/received"), null).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void nonActivitiesAreNotActivities() throws Exception {
        Post ask = posts.save(Post.builder().authorUser(host).intentType(PostIntentType.ASK).body("Need a ladder")
                .visibility(PostVisibility.PUBLIC).build());
        mvc.perform(get("/activities/" + ask.getId())).andExpect(status().isNotFound());
    }

    // --- helpers ---

    private long publicJoinCount(User u) {
        em.flush();
        return joins.countApprovedByUserIdIn(List.of(u.getId()), Instant.now().minus(ActivityRules.DISPUTE_WINDOW)).stream()
                .mapToLong(PostJoinRequestRepository.UserJoinCountProjection::getCnt).sum();
    }

    private void age(String joinId, int hours) {
        ActivityAttendance a = attendance.findByJoinRequestId(UUID.fromString(joinId)).orElseThrow();
        a.setOutcomeRecordedAt(Instant.now().minus(hours, ChronoUnit.HOURS));
        attendance.save(a);
    }

    private void started(Post p) {
        Post fresh = posts.findById(p.getId()).orElseThrow();
        fresh.setStartsAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        posts.save(fresh);
    }

    private Post activity(Integer capacity, PostVisibility visibility, Instant startsAt) {
        return posts.save(Post.builder().authorUser(host).intentType(PostIntentType.ACTIVITY).body("Sunrise run at the lake")
                .visibility(visibility).capacity(capacity).startsAt(startsAt).build());
    }

    private ResultActions call(User as, MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header("Authorization", bearer(as));
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

    private String bearer(User u) {
        return "Bearer " + tokens.generateToken(u.getId(), u.getEmail(), u.getName(), u.getRole());
    }
}
