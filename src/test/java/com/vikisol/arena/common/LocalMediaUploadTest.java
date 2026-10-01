package com.vikisol.arena.common;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
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

// B12: with Cloudinary unconfigured (the default in every test) and the 'local' profile active,
// POST /media/upload-signature points at this server's own POST /media/local-upload instead of
// refusing uploads outright, and a post can actually carry a locally-stored photo end to end.
@AutoConfigureMockMvc
@ActiveProfiles("local")
class LocalMediaUploadTest extends EmbeddedPostgresAppTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired com.vikisol.arena.common.service.FileSigningService fileSigningService;
    @MockBean TokenDenylistService denylist;

    private User asha;

    @BeforeEach
    void setUp() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        asha = users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Asha")
                .role(Role.TALENT).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
    }

    @Test
    void aSignatureFromUploadSignaturePointsAtLocalUploadAndWorksEndToEnd() throws Exception {
        JsonNode sig = body(call(asha, post("/media/upload-signature"), null)).path("data");
        assertThat(sig.path("cloudName").asText()).isEqualTo("local");
        assertThat(sig.path("uploadUrl").asText()).endsWith("/media/local-upload");

        String secureUrl = body(call(asha, multipart("/media/local-upload").file(png())
                        .param("folder", sig.path("folder").asText())
                        .param("timestamp", sig.path("timestamp").asText())
                        .param("signature", sig.path("signature").asText()), null)
                .andExpect(status().isOk()))
                .path("secure_url").asText();
        assertThat(secureUrl).contains("/files/post-media/");
        assertThat(secureUrl).doesNotContain("sig="); // bare - signed fresh on read, not at upload

        String postId = body(call(asha, post("/posts"),
                "{\"intentType\":\"update\",\"body\":\"A photo\",\"mediaUrls\":[\"" + secureUrl + "\"]}"))
                .path("data").path("id").asText();

        JsonNode read = body(call(asha, get("/posts/" + postId), null)).path("data");
        String signedUrl = read.path("mediaUrls").get(0).asText();
        assertThat(signedUrl).contains("sig=").contains("exp=");

        // The signature verifies against the exact path FileController serves it at (context
        // path included - see FileController's own comment on why that has to match exactly).
        java.net.URI uri = java.net.URI.create(signedUrl);
        var params = org.springframework.web.util.UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        assertThat(fileSigningService.verify(uri.getPath(), Long.parseLong(params.getFirst("exp")), params.getFirst("sig"))).isTrue();
    }

    @Test
    void localUploadRejectsATamperedSignatureOverHttp() throws Exception {
        call(asha, multipart("/media/local-upload").file(png())
                        .param("folder", "post-media").param("timestamp", "1700000000").param("signature", "forged"), null)
                .andExpect(status().isBadRequest());
    }

    @Test
    void aForeignUrlCantBeAttachedToAPostEvenInLocalFallback() throws Exception {
        call(asha, post("/posts"), "{\"intentType\":\"update\",\"body\":\"x\",\"mediaUrls\":[\"https://evil.example/pixel.gif\"]}")
                .andExpect(status().isBadRequest());
    }

    private ResultActions call(User as, MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header("Authorization", "Bearer " + tokens.generateToken(as.getId(), as.getEmail(), as.getName(), as.getRole()));
        if (body != null) request.contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static MockMultipartFile png() {
        return new MockMultipartFile("file", "shot.png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0});
    }
}
