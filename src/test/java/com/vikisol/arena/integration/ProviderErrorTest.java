package com.vikisol.arena.integration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.embedding.EmbeddingProvider;
import com.vikisol.arena.integration.provider.EmailProvider;
import com.vikisol.arena.integration.provider.PhoneOtpProvider;
import com.vikisol.arena.integration.provider.ProviderException;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.entity.ConsentSettings;
import com.vikisol.arena.profile.entity.Industry;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import com.vikisol.arena.schema.EmbeddedPostgresAppTest;
import com.vikisol.arena.security.jwt.JwtTokenProvider;
import com.vikisol.arena.security.jwt.TokenDenylistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A failing email/SMS/OpenAI provider never shows its own text to the user. The response is a
 * 503 with a short honest message; the provider's detail is logged server-side, redacted.
 */
@AutoConfigureMockMvc
class ProviderErrorTest extends EmbeddedPostgresAppTest {

    // What MSG91 / Resend really send back on a bad key: the recipient and even the code.
    private static final String RAW = "{\"message\":\"Invalid authkey re_9f8e7d6c5b4a39281706f5e4d3c2b1a0 for +919876543210, OTP 482913, to asha@example.com\"}";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CandidateProfileRepository profiles;
    @Autowired JwtTokenProvider tokens;
    @MockBean TokenDenylistService denylist;
    @MockBean PhoneOtpProvider phoneOtpProvider;
    @MockBean EmailProvider emailProvider;
    @MockBean EmbeddingProvider embeddingProvider;

    private final Logger providerLog = (Logger) LoggerFactory.getLogger("test.provider");
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void setUp() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        logs.start();
        providerLog.addAppender(logs);
    }

    @Test
    void aFailedSmsCodeIsAShortHonest503() throws Exception {
        doThrow(ProviderException.failure(providerLog, ProviderException.Kind.CODE, "MSG91", 401, RAW))
                .when(phoneOtpProvider).sendOtp(anyString(), anyString());
        String phone = "+9198765" + (10000 + (int) (Math.random() * 89999));
        users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Asha")
                .role(Role.TALENT).phoneNumber(phone).phoneVerified(true).dateOfBirth(LocalDate.of(1990, 1, 1)).build());

        String body = mvc.perform(post("/auth/phone/signin/request-otp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"" + phone + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("We couldn't send the code right now. Please try again in a minute."))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("MSG91", "authkey", "482913", "9876543210", "asha@example.com");

        // The full detail is in the server log - redacted.
        String logged = logs.list.get(0).getFormattedMessage();
        assertThat(logged).contains("MSG91", "CODE", "HTTP 401", "Invalid authkey", "[number]", "[email]", "[redacted]");
        assertThat(logged).doesNotContain("482913", "9876543210", "asha@example.com", "re_9f8e7d6c5b4a39281706f5e4d3c2b1a0");
    }

    @Test
    void aFailedSignInCodeEmailIsTheSameMessage() throws Exception {
        doThrow(ProviderException.failure(providerLog, ProviderException.Kind.EMAIL, "Resend", 403, RAW))
                .when(emailProvider).sendEmail(any());
        User user = users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Ravi")
                .role(Role.TALENT).dateOfBirth(LocalDate.of(1990, 1, 1)).build());

        String body = mvc.perform(post("/auth/email/signin/request-otp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("We couldn't send the code right now. Please try again in a minute."))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Resend", "authkey", "asha@example.com");
    }

    @Test
    void anOpenAiOutageDoesNotBreakTheFeed() throws Exception {
        when(embeddingProvider.embed(any()))
                .thenThrow(ProviderException.failure(providerLog, ProviderException.Kind.EMBEDDING, "OpenAI", 500, RAW));
        User user = users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Meera")
                .role(Role.TALENT).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
        profiles.save(CandidateProfile.builder().user(user).name("Meera").avatarEmoji("*").title("Engineer")
                .industry(Industry.ENGINEERING).location("Hyderabad").remote(false).bio("Loves football")
                .consent(new ConsentSettings(false, true)).build());

        mvc.perform(get("/posts/feed").header("Authorization",
                        "Bearer " + tokens.generateToken(user.getId(), user.getEmail(), user.getName(), user.getRole())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void theExceptionMessageIsAlwaysTheUserText() {
        for (ProviderException.Kind kind : ProviderException.Kind.values()) {
            ProviderException e = ProviderException.failure(providerLog, kind, "Any", 500, RAW);
            assertThat(e.getMessage()).isEqualTo(kind.userMessage()).doesNotContain("authkey");
            assertThat(e.getCause()).isNull();
        }
    }
}
