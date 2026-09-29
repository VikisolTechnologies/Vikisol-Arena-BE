package com.vikisol.arena.performance;

import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.dto.PageLimits;
import com.vikisol.arena.follows.entity.Follow;
import com.vikisol.arena.follows.repository.FollowRepository;
import com.vikisol.arena.schema.EmbeddedPostgresAppTest;
import com.vikisol.arena.security.jwt.JwtTokenProvider;
import com.vikisol.arena.security.jwt.TokenDenylistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * List endpoints that used to return every row now take optional page/size. The JSON stays a bare
 * array (the frontend's shape), a client can't ask for more than PageLimits.MAX_SIZE rows, and
 * nonsense page/size values are clamped rather than turned into errors.
 */
@AutoConfigureMockMvc
class ListPaginationTest extends EmbeddedPostgresAppTest {

    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired FollowRepository follows;
    @MockBean TokenDenylistService denylist;

    @BeforeEach
    void signedInTokensAreNotRevoked() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
    }

    @Test
    void followersKeepTheirArrayShapeAndPage() throws Exception {
        User me = user();
        for (int i = 0; i < 3; i++) follows.save(Follow.builder().followerUser(user()).followingUser(me).build());
        String auth = "Bearer " + token(me);

        mvc.perform(get("/follows/me/followers").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3));
        mvc.perform(get("/follows/me/followers").param("size", "2").header("Authorization", auth))
                .andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(get("/follows/me/followers").param("page", "1").param("size", "2").header("Authorization", auth))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void aClientCannotAskForMoreThanTheMaximum() throws Exception {
        User me = user();
        for (int i = 0; i < PageLimits.MAX_SIZE + 1; i++) {
            follows.save(Follow.builder().followerUser(user()).followingUser(me).build());
        }

        mvc.perform(get("/follows/me/followers").param("size", "100000").param("page", "-3")
                        .header("Authorization", "Bearer " + token(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(PageLimits.MAX_SIZE));
    }

    @Test
    void pagedEndpointsClampSizeAndPage() throws Exception {
        mvc.perform(get("/posts/feed").param("size", "100000").param("page", "-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
        mvc.perform(get("/jobs").param("size", "100000").param("page", "-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(PageLimits.MAX_SIZE))
                .andExpect(jsonPath("$.data.page").value(0));
    }

    @Test
    void sliceCutsARankedList() {
        List<Integer> all = IntStream.range(0, 5).boxed().toList();
        assertThat(PageLimits.slice(all, 0, 2)).containsExactly(0, 1);
        assertThat(PageLimits.slice(all, 2, 2)).containsExactly(4);
        assertThat(PageLimits.slice(all, 3, 2)).isEmpty();
        assertThat(PageLimits.slice(all, -1, 0)).containsExactly(0);
    }

    private User user() {
        return users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x")
                .name("Person").role(Role.TALENT).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
    }

    private String token(User user) {
        return tokens.generateToken(user.getId(), user.getEmail(), user.getName(), user.getRole());
    }
}
