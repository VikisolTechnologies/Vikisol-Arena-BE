package com.vikisol.arena.posts;

import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.entity.PostIntentType;
import com.vikisol.arena.posts.entity.PostJoinRequest;
import com.vikisol.arena.posts.entity.PostJoinStatus;
import com.vikisol.arena.posts.entity.PostVisibility;
import com.vikisol.arena.posts.repository.PostJoinRequestRepository;
import com.vikisol.arena.posts.repository.PostRepository;
import com.vikisol.arena.schema.EmbeddedPostgresAppTest;
import com.vikisol.arena.security.jwt.JwtTokenProvider;
import com.vikisol.arena.security.jwt.TokenDenylistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /posts/joined is the caller's own approved joins. A guest must be turned away before
 * the controller runs, and one signed-in user must not see another user's joins.
 */
@AutoConfigureMockMvc
class PostJoinedAccessTest extends EmbeddedPostgresAppTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired PostRepository posts;
    @Autowired PostJoinRequestRepository joins;
    @MockBean TokenDenylistService denylist;

    @BeforeEach
    void signedInTokensAreNotRevoked() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
    }

    @Test
    void aGuestIsUnauthorized() throws Exception {
        mvc.perform(get("/posts/joined"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aUserSeesOnlyTheirOwnJoins() throws Exception {
        User host = user();
        Post activity = posts.save(Post.builder()
                .authorUser(host)
                .intentType(PostIntentType.ACTIVITY)
                .body("Evening game")
                .visibility(PostVisibility.PUBLIC)
                .build());
        User member = user();
        User someoneElse = user();
        joins.save(PostJoinRequest.builder()
                .post(activity)
                .user(member)
                .status(PostJoinStatus.APPROVED)
                .build());

        mvc.perform(get("/posts/joined").header("Authorization", "Bearer " + token(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(activity.getId().toString()))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mvc.perform(get("/posts/joined").header("Authorization", "Bearer " + token(someoneElse)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    private User user() {
        return users.save(User.builder()
                .email(UUID.randomUUID() + "@test.local")
                .passwordHash("x")
                .name("Participant")
                .role(Role.TALENT)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .build());
    }

    private String token(User user) {
        return tokens.generateToken(user.getId(), user.getEmail(), user.getName(), user.getRole());
    }
}
