package com.vikisol.arena.performance;

import com.vikisol.arena.agent.entity.AgentConversation;
import com.vikisol.arena.agent.entity.AgentMessage;
import com.vikisol.arena.agent.entity.AgentMessageRole;
import com.vikisol.arena.agent.repository.AgentConversationRepository;
import com.vikisol.arena.agent.repository.AgentMessageRepository;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.dto.PageLimits;
import com.vikisol.arena.follows.entity.Follow;
import com.vikisol.arena.follows.repository.FollowRepository;
import com.vikisol.arena.messaging.entity.Conversation;
import com.vikisol.arena.messaging.repository.ConversationRepository;
import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.entity.PostIntentType;
import com.vikisol.arena.posts.entity.PostJoinRequest;
import com.vikisol.arena.posts.entity.PostJoinStatus;
import com.vikisol.arena.posts.entity.PostVisibility;
import com.vikisol.arena.posts.repository.PostJoinRequestRepository;
import com.vikisol.arena.posts.repository.PostRepository;
import com.vikisol.arena.rooms.entity.Room;
import com.vikisol.arena.rooms.entity.RoomMember;
import com.vikisol.arena.rooms.entity.RoomMessage;
import com.vikisol.arena.rooms.repository.RoomMemberRepository;
import com.vikisol.arena.rooms.repository.RoomMessageRepository;
import com.vikisol.arena.rooms.repository.RoomRepository;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The capped lists put the most recent rows on page 0, and say how many rows exist and whether
 * another page follows in X-Total-Count / X-Has-More - the body stays a bare array.
 */
@AutoConfigureMockMvc
class ListOrderAndHeadersTest extends EmbeddedPostgresAppTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired FollowRepository follows;
    @Autowired ConversationRepository conversations;
    @Autowired PostRepository posts;
    @Autowired RoomRepository rooms;
    @Autowired RoomMemberRepository roomMembers;
    @Autowired RoomMessageRepository roomMessages;
    @Autowired PostJoinRequestRepository joins;
    @Autowired AgentConversationRepository agentConversations;
    @Autowired AgentMessageRepository agentMessages;
    @MockBean TokenDenylistService denylist;

    private User me;
    private String auth;

    @BeforeEach
    void signedIn() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        me = user("Me");
        auth = "Bearer " + tokens.generateToken(me.getId(), me.getEmail(), me.getName(), me.getRole());
    }

    @Test
    void followersNewestFirstWithPagingHeaders() throws Exception {
        User first = user("First"), second = user("Second"), third = user("Third");
        for (User u : new User[]{first, second, third}) {
            follows.save(Follow.builder().followerUser(u).followingUser(me).build());
            tick();
        }

        list("/follows/me/followers", 0, 2)
                .andExpect(jsonPath("$.data[0].userId").value(third.getId().toString()))
                .andExpect(jsonPath("$.data[1].userId").value(second.getId().toString()))
                .andExpect(header().string(PageLimits.TOTAL_COUNT_HEADER, "3"))
                .andExpect(header().string(PageLimits.HAS_MORE_HEADER, "true"));
        list("/follows/me/followers", 1, 2)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].userId").value(first.getId().toString()))
                .andExpect(header().string(PageLimits.HAS_MORE_HEADER, "false"));
    }

    @Test
    void conversationsMostRecentMessageFirst() throws Exception {
        Instant now = Instant.now();
        Conversation older = conversation(now.minus(3, ChronoUnit.HOURS));
        Conversation newest = conversation(now.minus(1, ChronoUnit.HOURS));
        Conversation middle = conversation(now.minus(2, ChronoUnit.HOURS));

        list("/messages/conversations", 0, 100)
                .andExpect(jsonPath("$.data[0].id").value(newest.getId().toString()))
                .andExpect(jsonPath("$.data[1].id").value(middle.getId().toString()))
                .andExpect(jsonPath("$.data[2].id").value(older.getId().toString()))
                .andExpect(header().string(PageLimits.TOTAL_COUNT_HEADER, "3"))
                .andExpect(header().string(PageLimits.HAS_MORE_HEADER, "false"));
    }

    @Test
    void roomsMostRecentActivityFirst() throws Exception {
        // Joined A, then B. Then someone writes in A: A is now the most recent, even though B
        // was joined later.
        Room a = room(), b;
        roomMembers.save(RoomMember.builder().room(a).user(me).build());
        tick();
        b = room();
        roomMembers.save(RoomMember.builder().room(b).user(me).build());
        tick();
        roomMessages.save(RoomMessage.builder().room(a).sender(me).content("Running late").build());

        list("/rooms", 0, 1)
                .andExpect(jsonPath("$.data[0].id").value(a.getId().toString()))
                .andExpect(header().string(PageLimits.TOTAL_COUNT_HEADER, "2"))
                .andExpect(header().string(PageLimits.HAS_MORE_HEADER, "true"));
        list("/rooms", 1, 1)
                .andExpect(jsonPath("$.data[0].id").value(b.getId().toString()));
    }

    @Test
    void jennyPageZeroIsTheLatestMessages() throws Exception {
        AgentConversation chat = agentConversations.save(AgentConversation.builder().user(me).title("Chat").build());
        for (int i = 1; i <= 5; i++) {
            agentMessages.save(AgentMessage.builder().conversation(chat)
                    .role(i % 2 == 1 ? AgentMessageRole.USER : AgentMessageRole.AGENT).content("m" + i).build());
            tick();
        }

        // Newest two, read top to bottom.
        list("/agent/conversations/" + chat.getId() + "/messages", 0, 2)
                .andExpect(jsonPath("$.data[0].content").value("m4"))
                .andExpect(jsonPath("$.data[1].content").value("m5"))
                .andExpect(header().string(PageLimits.TOTAL_COUNT_HEADER, "5"))
                .andExpect(header().string(PageLimits.HAS_MORE_HEADER, "true"));
        list("/agent/conversations/" + chat.getId() + "/messages", 2, 2)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].content").value("m1"))
                .andExpect(header().string(PageLimits.HAS_MORE_HEADER, "false"));
    }

    @Test
    void joinRequestsStayAQueueWithHeaders() throws Exception {
        Post activity = posts.save(Post.builder().authorUser(me).intentType(PostIntentType.ACTIVITY)
                .body("Evening game").visibility(PostVisibility.PUBLIC).build());
        User early = user("Early"), late = user("Late");
        joins.save(PostJoinRequest.builder().post(activity).user(early).status(PostJoinStatus.PENDING).build());
        tick();
        joins.save(PostJoinRequest.builder().post(activity).user(late).status(PostJoinStatus.PENDING).build());

        list("/posts/" + activity.getId() + "/joins", 0, 100)
                .andExpect(jsonPath("$.data[0].userId").value(early.getId().toString()))
                .andExpect(header().string(PageLimits.TOTAL_COUNT_HEADER, "2"));
    }

    @Test
    void communitiesCarryTheHeadersAndBrowsersCanReadThem() throws Exception {
        mvc.perform(get("/communities").header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(header().exists(PageLimits.TOTAL_COUNT_HEADER))
                .andExpect(header().exists(PageLimits.HAS_MORE_HEADER))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString(PageLimits.TOTAL_COUNT_HEADER)))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString(PageLimits.HAS_MORE_HEADER)));
    }

    @Test
    void preflightAnswersAreCachedForAnHour() throws Exception {
        mvc.perform(options("/communities").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Max-Age", "3600"));
    }

    private ResultActions list(String path, int page, int size) throws Exception {
        return mvc.perform(get(path).param("page", String.valueOf(page)).param("size", String.valueOf(size))
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    private Conversation conversation(Instant lastMessageAt) {
        return conversations.save(Conversation.builder().userA(me).userB(user("Other")).lastMessageAt(lastMessageAt).build());
    }

    private Room room() {
        Post post = posts.save(Post.builder().authorUser(me).intentType(PostIntentType.ACTIVITY)
                .body("Meetup").visibility(PostVisibility.PUBLIC).build());
        return rooms.save(Room.builder().post(post).build());
    }

    private User user(String name) {
        return users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x")
                .name(name).role(Role.TALENT).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
    }

    // createdAt comes from auditing at save time; keep consecutive rows apart.
    private static void tick() throws InterruptedException {
        Thread.sleep(3);
    }
}
