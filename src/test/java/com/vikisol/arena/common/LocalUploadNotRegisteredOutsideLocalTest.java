package com.vikisol.arena.common;

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
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// MARATHON-BE-2 step 1b item 3: POST /media/local-upload now lives in a @Profile("local")
// controller (LocalUploadController) - this test has no active profile (the suite's default), so
// the endpoint must not exist at all, not just refuse the request at the service layer.
@AutoConfigureMockMvc
class LocalUploadNotRegisteredOutsideLocalTest extends EmbeddedPostgresAppTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtTokenProvider tokens;
    @MockBean TokenDenylistService denylist;

    private User asha;

    @BeforeEach
    void setUp() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        asha = users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Asha")
                .role(Role.TALENT).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
    }

    @Test
    void localUploadDoesNotExistWithoutTheLocalProfile() throws Exception {
        var file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3});
        mvc.perform(multipart("/media/local-upload").file(file)
                        .param("folder", "post-media").param("timestamp", "1700000000").param("signature", "x")
                        .header("Authorization", "Bearer " + tokens.generateToken(asha.getId(), asha.getEmail(), asha.getName(), asha.getRole())))
                .andExpect(status().isNotFound());
    }
}
