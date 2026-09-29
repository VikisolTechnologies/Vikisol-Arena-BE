package com.vikisol.arena.posts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikisol.arena.activities.repository.PostReminderRepository;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.notifications.entity.Notification;
import com.vikisol.arena.notifications.repository.NotificationRepository;
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

import java.time.Duration;
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

/** FE-API-GAPS rows 14 and 39, flow A10 (edit) and A11 (cancel reason). */
@AutoConfigureMockMvc
class PostEditPauseTest extends EmbeddedPostgresAppTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired NotificationRepository notifications;
    @Autowired PostReminderRepository reminders;
    @MockBean TokenDenylistService denylist;

    private User owner, asha, ravi;

    @BeforeEach
    void people() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        owner = user();
        asha = user();
        ravi = user();
    }

    @Test
    void ownerEditsAndJoinersAreToldWhatChanged() throws Exception {
        String id = create("{\"intentType\":\"activity\",\"title\":\"Run\",\"body\":\"Morning run\",\"startsAt\":\"" + inDays(3) + "\"}");
        call(asha, post("/posts/" + id + "/joins"), null).andExpect(status().isOk());

        Instant newStart = Instant.now().plus(4, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        call(owner, patch("/posts/" + id), "{\"title\":\"Long run\",\"startsAt\":\"" + newStart + "\",\"exactMeetingPoint\":\"Gate 2\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Long run"))
                .andExpect(jsonPath("$.data.editedAt").exists());

        Notification told = notifications.findByUserIdOrderByCreatedAtDesc(asha.getId(), PageRequest.of(0, 1)).getContent().get(0);
        assertThat(told.getBody()).contains("title").contains("start time").contains("meeting point");
        // Default reminders moved with the start.
        assertThat(reminders.findByPostIdAndUserId(UUID.fromString(id), asha.getId()))
                .allSatisfy(r -> assertThat(r.getRemindAt()).isEqualTo(newStart.minus(Duration.ofMinutes(r.getMinutesBefore()))));

        call(asha, patch("/posts/" + id), "{\"title\":\"Mine now\"}").andExpect(status().isForbidden());
        call(owner, patch("/posts/" + id), "{\"body\":\" \"}").andExpect(status().isBadRequest());
        call(owner, patch("/posts/" + id), "{\"startsAt\":\"" + Instant.now().minus(1, ChronoUnit.HOURS) + "\"}").andExpect(status().isBadRequest());
        call(owner, put("/posts/" + id + "/cancel"), null).andExpect(status().isOk());
        call(owner, patch("/posts/" + id), "{\"title\":\"Too late\"}").andExpect(status().isBadRequest());
    }

    @Test
    void aPausedNeedIsHiddenAndTakesNoOffersUntilReopened() throws Exception {
        String id = create("{\"intentType\":\"ask\",\"body\":\"Need help moving a sofa\"}");
        call(asha, put("/posts/" + id + "/status"), "{\"status\":\"paused\"}").andExpect(status().isForbidden());
        call(owner, put("/posts/" + id + "/status"), "{\"status\":\"paused\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("paused"));

        call(asha, get("/posts/feed"), null).andExpect(jsonPath("$.data[*].id", not(hasItem(id))));
        call(asha, get("/posts/by-user/" + owner.getId()), null).andExpect(jsonPath("$.data.content[*].id", not(hasItem(id))));
        call(owner, get("/posts/by-user/" + owner.getId()), null).andExpect(jsonPath("$.data.content[*].id", hasItem(id)));
        call(asha, post("/needs/" + id + "/responses"), "{\"message\":\"I can help\"}").andExpect(status().isBadRequest());

        call(owner, put("/posts/" + id + "/status"), "{\"status\":\"open\"}")
                .andExpect(jsonPath("$.data.status").value("open"));
        call(asha, post("/needs/" + id + "/responses"), "{\"message\":\"I can help\"}").andExpect(status().isOk());
        // Close still works from paused, and with no body (the original call).
        call(owner, put("/posts/" + id + "/status"), "{\"status\":\"paused\"}").andExpect(status().isOk());
        call(owner, put("/posts/" + id + "/status"), null).andExpect(jsonPath("$.data.status").value("closed"));
        call(owner, put("/posts/" + id + "/status"), "{\"status\":\"open\"}").andExpect(status().isBadRequest());
    }

    @Test
    void onlyNeedsAndOffersPause() throws Exception {
        String id = create("{\"intentType\":\"activity\",\"body\":\"Chess\",\"startsAt\":\"" + inDays(2) + "\"}");
        call(owner, put("/posts/" + id + "/status"), "{\"status\":\"paused\"}").andExpect(status().isBadRequest());
        call(owner, put("/posts/" + id + "/status"), "{\"status\":\"sleeping\"}").andExpect(status().isBadRequest());
    }

    @Test
    void cancelReasonReachesEveryoneWhoJoined() throws Exception {
        String id = create("{\"intentType\":\"activity\",\"body\":\"Badminton doubles\",\"startsAt\":\"" + inDays(2) + "\"}");
        call(asha, post("/posts/" + id + "/joins"), null).andExpect(status().isOk());
        call(ravi, post("/posts/" + id + "/joins"), null).andExpect(status().isOk());
        call(owner, put("/posts/" + id + "/cancel"), "{\"reason\":\"The court is closed for repairs\"}")
                .andExpect(jsonPath("$.data.status").value("cancelled"))
                .andExpect(jsonPath("$.data.cancelReason").value("The court is closed for repairs"));
        for (User joined : new User[]{asha, ravi}) {
            Notification n = notifications.findByUserIdOrderByCreatedAtDesc(joined.getId(), PageRequest.of(0, 1)).getContent().get(0);
            assertThat(n.getTitle()).isEqualTo("Activity cancelled");
            assertThat(n.getBody()).contains("The court is closed for repairs");
        }
    }

    private static String inDays(int days) {
        return Instant.now().plus(days, ChronoUnit.DAYS).toString();
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

    private User user() {
        return users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Person")
                .role(Role.TALENT).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
    }
}
