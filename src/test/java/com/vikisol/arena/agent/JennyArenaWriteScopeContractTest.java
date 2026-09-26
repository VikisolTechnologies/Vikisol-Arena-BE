package com.vikisol.arena.agent;

import com.vikisol.arena.agent.client.AgentServiceTokenIssuer;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.schema.EmbeddedPostgresAppTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Jenny's writes are refused on the public API when the service token is not allowed to make
 * them. The body shapes stay in {@link JennyArenaWriteBodyContractTest}. This class does not
 * call the package-private scope map; it sends the token the way the gateway does.
 */
@AutoConfigureMockMvc
class JennyArenaWriteScopeContractTest extends EmbeddedPostgresAppTest {

    private static final String SECRET = "contract-test-service-secret-not-for-production-123";
    private static final String POST_BODY = """
            {"intentType":"ask","title":"Cycle","body":"Where to buy a cycle?","anonymous":true,"audience":"global","visibility":"public","tags":[],"mediaUrls":[]}
            """;

    @DynamicPropertySource
    static void serviceTokenSecret(DynamicPropertyRegistry registry) {
        registry.add("app.agent.service-token-secret", () -> SECRET);
    }

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AgentServiceTokenIssuer issuer;

    @Test
    void aServiceTokenThatLacksTheScopeIsForbidden() throws Exception {
        User talent = user(Role.TALENT);
        String token = issuer.issue(talent.getId().toString(), "TALENT", null, List.of("arena.searchJobs"));

        mvc.perform(post("/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(POST_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void aTokenForADifferentUserIsForbidden() throws Exception {
        User talent = user(Role.TALENT);
        User someoneElse = user(Role.COMPANY_ADMIN);
        // The scope claim names the write, but the subject is not a talent. The post endpoint
        // stays talent-only, so this token must not publish as the other user.
        String token = issuer.issue(someoneElse.getId().toString(), "COMPANY_ADMIN", null, List.of("arena.createPost"));

        mvc.perform(post("/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(POST_BODY))
                .andExpect(status().isForbidden());

        // A second talent, also without the write scope, is refused the same way.
        String otherTalent = issuer.issue(talent.getId().toString(), "TALENT", null, List.of("arena.joinActivity"));
        mvc.perform(post("/posts")
                        .header("Authorization", "Bearer " + otherTalent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(POST_BODY))
                .andExpect(status().isForbidden());
    }

    private User user(Role role) {
        return users.save(User.builder()
                .name("Contract")
                .email(UUID.randomUUID() + "@test.local")
                .passwordHash("x")
                .role(role)
                .build());
    }
}
