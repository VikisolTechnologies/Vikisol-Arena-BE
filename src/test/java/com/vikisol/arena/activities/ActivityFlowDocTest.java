package com.vikisol.arena.activities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikisol.arena.activities.entity.PostReminder;
import com.vikisol.arena.activities.repository.ActivityEmergencyContactRepository;
import com.vikisol.arena.activities.repository.PostReminderRepository;
import com.vikisol.arena.activities.service.ActivityHousekeeping;
import com.vikisol.arena.activities.service.ReminderService;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.notifications.repository.NotificationRepository;
import com.vikisol.arena.posts.entity.Post;
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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Activities per ARENA-APP-FLOW §3 and FE-API-GAPS rows 7, 8, 10, 23, 25. */
@AutoConfigureMockMvc
class ActivityFlowDocTest extends EmbeddedPostgresAppTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired PostRepository posts;
    @Autowired PostReminderRepository reminders;
    @Autowired ActivityEmergencyContactRepository contacts;
    @Autowired ReminderService reminderService;
    @Autowired ActivityHousekeeping housekeeping;
    @Autowired NotificationRepository notifications;
    @Autowired EntityManager em;
    @MockBean TokenDenylistService denylist;

    private User host, asha, ravi;

    @BeforeEach
    void people() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        host = user();
        asha = user();
        ravi = user();
    }

    @Test
    void oneCallCreatesTheActivityItsDetailsAndQuestions() throws Exception {
        String id = create("{\"intentType\":\"activity\",\"title\":\"Box cricket\",\"body\":\"Sunday box cricket\",\"startsAt\":\"" + inDays(3) + "\","
                + "\"activity\":{\"category\":\"sports\",\"subtype\":\"cricket\",\"level\":\"intermediate\","
                + "\"cost\":{\"type\":\"shared\",\"perPersonInr\":150,\"note\":\"Turf booking\"},"
                + "\"typeAnswers\":{\"format\":\"box\",\"overs\":6,\"equipmentProvided\":[\"bat\",\"ball\"],\"groundBooked\":true},"
                + "\"bring\":[\"Water\",\"Sports shoes\"],\"indoor\":false,\"repeat\":\"weekly\"},"
                + "\"hostQuestions\":[\"How many overs have you played?\"]}");
        mvc.perform(get("/activities/" + id))
                .andExpect(jsonPath("$.data.subtype").value("cricket"))
                .andExpect(jsonPath("$.data.cost.perPersonInr").value(150))
                .andExpect(jsonPath("$.data.typeAnswers.overs").value(6))
                .andExpect(jsonPath("$.data.typeAnswers.equipmentProvided[1]").value("ball"))
                .andExpect(jsonPath("$.data.bring[0]").value("Water"))
                .andExpect(jsonPath("$.data.repeat").value("weekly"))
                .andExpect(jsonPath("$.data.questions[0].text").value("How many overs have you played?"));
        // Row 8 and row 10 on the post itself.
        mvc.perform(get("/posts/" + id))
                .andExpect(jsonPath("$.data.priceInr").value(150))
                .andExpect(jsonPath("$.data.authorVerificationLevel").exists());
        // All or nothing: a bad subtype creates nothing.
        call(host, post("/posts"), "{\"intentType\":\"activity\",\"body\":\"x\",\"activity\":{\"category\":\"sports\",\"subtype\":\"yoga\"}}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void womenOnlyIsALabelThatMakesItApprovalOnly() throws Exception {
        String id = create("{\"intentType\":\"activity\",\"body\":\"Evening walk\",\"visibility\":\"public\",\"startsAt\":\"" + inDays(2) + "\","
                + "\"activity\":{\"category\":\"fitness\",\"subtype\":\"walking\",\"womenOnly\":true}}");
        mvc.perform(get("/posts/" + id)).andExpect(jsonPath("$.data.visibility").value("approval"));
        mvc.perform(get("/activities/" + id)).andExpect(jsonPath("$.data.womenOnly").value(true));
    }

    @Test
    void linkOnlyActivitiesStayOutOfDiscovery() throws Exception {
        String id = create("{\"intentType\":\"activity\",\"body\":\"Private chess club\",\"startsAt\":\"" + inDays(2) + "\","
                + "\"activity\":{\"category\":\"games\",\"subtype\":\"chess\",\"reach\":\"link\"}}");
        call(asha, get("/posts/feed"), null).andExpect(jsonPath("$.data[*].id", not(hasItem(id))));
        call(asha, get("/posts/by-user/" + host.getId()), null).andExpect(jsonPath("$.data.content[*].id", not(hasItem(id))));
        call(asha, get("/posts/" + id), null).andExpect(status().isOk()); // the link still works
        call(host, get("/posts/by-user/" + host.getId()), null).andExpect(jsonPath("$.data.content[*].id", hasItem(id)));
    }

    @Test
    void trekEmergencyContactsAreHostOnlyAfterApprovalAndDeletedAfterwards() throws Exception {
        String id = create("{\"intentType\":\"activity\",\"body\":\"Ananthagiri trek\",\"visibility\":\"approval\",\"startsAt\":\"" + inDays(2) + "\","
                + "\"activity\":{\"category\":\"outdoors\",\"subtype\":\"trekking\"}}");
        mvc.perform(get("/activities/" + id)).andExpect(jsonPath("$.data.needsEmergencyContact").value(true));
        call(asha, post("/activities/" + id + "/join"), "{}").andExpect(status().isBadRequest());
        String joinId = body(call(asha, post("/activities/" + id + "/join"),
                "{\"note\":\"First trek!\",\"emergencyContact\":{\"name\":\"Lakshmi\",\"phone\":\"+91 98765 43210\"}}")
                .andExpect(status().isOk())).path("data").path("id").asText();

        call(host, get("/activities/" + id + "/emergency-contacts"), null).andExpect(jsonPath("$.data.length()").value(0)); // not approved yet
        call(host, get("/posts/" + id + "/joins"), null).andExpect(jsonPath("$.data[0].note").value("First trek!"));
        call(host, put("/posts/" + id + "/joins/" + joinId + "/approve"), "{\"note\":\"Meet at the gate at 6\"}")
                .andExpect(jsonPath("$.data.decisionNote").value("Meet at the gate at 6"));
        call(host, get("/activities/" + id + "/emergency-contacts"), null)
                .andExpect(jsonPath("$.data[0].contactName").value("Lakshmi"));
        call(ravi, get("/activities/" + id + "/emergency-contacts"), null).andExpect(status().isForbidden());

        Post trek = posts.findById(UUID.fromString(id)).orElseThrow();
        trek.setStartsAt(Instant.now().minus(3, ChronoUnit.DAYS));
        trek.setEndsAt(Instant.now().minus(2, ChronoUnit.DAYS));
        posts.save(trek);
        em.flush();
        housekeeping.deleteFinishedEmergencyContacts();
        assertThat(contacts.findByPostId(trek.getId())).isEmpty();
    }

    @Test
    void hostCheckInAndTheJoinersOwnConfirmation() throws Exception {
        String id = create("{\"intentType\":\"activity\",\"body\":\"Badminton doubles\",\"startsAt\":\"" + inDays(1) + "\","
                + "\"activity\":{\"category\":\"sports\",\"subtype\":\"badminton\"}}");
        String ashaJoin = body(call(asha, post("/posts/" + id + "/joins"), null)).path("data").path("id").asText();
        String raviJoin = body(call(ravi, post("/posts/" + id + "/joins"), null)).path("data").path("id").asText();
        call(host, put("/activities/" + id + "/attendance/" + ashaJoin + "/check-in"), null).andExpect(status().isBadRequest()); // too early
        started(id);

        call(asha, put("/activities/" + id + "/attendance/" + ashaJoin + "/check-in"), null).andExpect(status().isForbidden());
        call(host, put("/activities/" + id + "/attendance/" + ashaJoin + "/check-in"), null)
                .andExpect(jsonPath("$.data[0].outcome").value("attended"))
                .andExpect(jsonPath("$.data[0].checkedInAt").exists());
        call(asha, post("/activities/" + id + "/attendance/confirm"), "{\"attended\":true}")
                .andExpect(jsonPath("$.data.viewer.attendedConfirmed").value(true));

        call(host, put("/posts/" + id + "/joins/" + raviJoin + "/outcome"), "{\"outcome\":\"no_show\"}").andExpect(status().isOk());
        call(ravi, post("/activities/" + id + "/attendance/confirm"), "{\"attended\":true}").andExpect(status().isBadRequest());
        call(ravi, post("/activities/" + id + "/attendance/confirm"), "{\"attended\":true,\"dispute\":\"I was on court 2\"}")
                .andExpect(jsonPath("$.data.viewer.disputeStatus").value("open"));
    }

    @Test
    void approvedJoinersGetDefaultRemindersAndCanManageTheirOwn() throws Exception {
        String id = create("{\"intentType\":\"activity\",\"body\":\"Yoga in the park\",\"startsAt\":\"" + inDays(3) + "\","
                + "\"activity\":{\"category\":\"fitness\",\"subtype\":\"yoga\"}}");
        call(ravi, post("/posts/" + id + "/reminder"), "{\"minutesBefore\":60}").andExpect(status().isForbidden()); // not joined
        call(asha, post("/posts/" + id + "/joins"), null).andExpect(status().isOk());
        call(asha, get("/activities/" + id), null).andExpect(jsonPath("$.data.viewer.reminders[0]").value(120));
        call(asha, post("/posts/" + id + "/reminder"), "{\"minutesBefore\":60}")
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0]").value(60));
        call(asha, delete("/posts/" + id + "/reminder").param("minutesBefore", "1440"), null)
                .andExpect(jsonPath("$.data.length()").value(2));

        // A due reminder is sent once, as a notification.
        PostReminder due = reminders.findByPostIdAndUserId(UUID.fromString(id), asha.getId()).get(0);
        due.setRemindAt(Instant.now().minusSeconds(5));
        reminders.save(due);
        em.flush();
        reminderService.sendDue();
        assertThat(notifications.findByUserIdOrderByCreatedAtDesc(asha.getId(), PageRequest.of(0, 20)).getContent())
                .anyMatch(n -> n.getTitle().equals("Reminder"));
    }

    private void started(String id) {
        Post p = posts.findById(UUID.fromString(id)).orElseThrow();
        p.setStartsAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        posts.save(p);
    }

    private static String inDays(int days) {
        return Instant.now().plus(days, ChronoUnit.DAYS).toString();
    }

    private String create(String body) throws Exception {
        return body(call(host, post("/posts"), body).andExpect(status().isOk())).path("data").path("id").asText();
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
