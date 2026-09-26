package com.vikisol.arena.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikisol.arena.applications.dto.ApplyRequest;
import com.vikisol.arena.marketplace.dto.CreateProjectRequest;
import com.vikisol.arena.marketplace.dto.PlaceBidRequest;
import com.vikisol.arena.posts.dto.CreatePostRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the JSON JennySol already sends in production
 * ({@code jennysol-ai/server/src/services/productConnectors/arena.ts}).
 * These bodies and the scope-to-path map must keep binding. Add fields only
 * as optional extras. Do not rename or remove the ones asserted here.
 */
class JennyArenaWriteBodyContractTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void createPostBodyStillBinds() throws Exception {
        String json = """
                {"intentType":"ask","title":"Cycle","body":"Where to buy a cycle?","locationText":null,"startsAt":null,"capacity":null,"communityId":null,"anonymous":true,"audience":"global","visibility":"public","tags":[],"mediaUrls":[]}
                """;
        CreatePostRequest request = mapper.readValue(json, CreatePostRequest.class);
        assertThat(request.intentType()).isEqualTo("ask");
        assertThat(request.title()).isEqualTo("Cycle");
        assertThat(request.body()).isEqualTo("Where to buy a cycle?");
        assertThat(request.anonymous()).isTrue();
        assertThat(request.audience()).isEqualTo("global");
        assertThat(request.visibility()).isEqualTo("public");
        assertThat(request.tags()).isEmpty();
        assertThat(request.mediaUrls()).isEmpty();
    }

    @Test
    void createProjectBodyStillBinds() throws Exception {
        String json = """
                {"title":"Need a site","description":"A small site","budgetMin":10000,"budgetMax":40000,"durationWeeks":3,"skills":["design"]}
                """;
        CreateProjectRequest request = mapper.readValue(json, CreateProjectRequest.class);
        assertThat(request.title()).isEqualTo("Need a site");
        assertThat(request.description()).isEqualTo("A small site");
        assertThat(request.budgetMin()).isEqualTo(10000);
        assertThat(request.budgetMax()).isEqualTo(40000);
        assertThat(request.durationWeeks()).isEqualTo(3);
        assertThat(request.skills()).containsExactly("design");
    }

    @Test
    void placeBidBodyStillBinds() throws Exception {
        PlaceBidRequest request = mapper.readValue("{\"amount\":50000}", PlaceBidRequest.class);
        assertThat(request.amount()).isEqualTo(50000);
    }

    @Test
    void applyToJobBodyStillBinds() throws Exception {
        ApplyRequest request = mapper.readValue("{\"jobId\":\"job-42\"}", ApplyRequest.class);
        assertThat(request.jobId()).isEqualTo("job-42");
    }
}
