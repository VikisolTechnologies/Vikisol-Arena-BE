package com.vikisol.arena.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.exception.GlobalExceptionHandler;
import com.vikisol.arena.schema.EmbeddedPostgresAppTest;
import com.vikisol.arena.security.jwt.JwtTokenProvider;
import com.vikisol.arena.security.jwt.TokenDenylistService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Every error the API returns is {"success": false, "message": "..."} - plus "data" only for
 * field-level validation errors - with the right HTTP status. Before this, an unknown route, a
 * wrong method, a missing parameter or an unsupported content type all came back as a 500, and
 * the servlet /error page used Spring Boot's own {timestamp, status, error, path} body.
 */
@AutoConfigureMockMvc
class ErrorContractTest extends EmbeddedPostgresAppTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired GlobalExceptionHandler handler;
    @MockBean TokenDenylistService denylist;

    private String auth;

    @BeforeEach
    void signedIn() {
        when(denylist.isDenylisted(anyString())).thenReturn(false);
        User user = users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x")
                .name("Person").role(Role.TALENT).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
        auth = "Bearer " + tokens.generateToken(user.getId(), user.getEmail(), user.getName(), user.getRole());
    }

    @Test
    void anUnknownRouteIsA404() throws Exception {
        assertError(get("/no-such-endpoint").header("Authorization", auth), 404, "Not found");
    }

    @Test
    void aWrongMethodIsA405() throws Exception {
        assertError(put("/version").header("Authorization", auth), 405, "This action isn't supported here");
    }

    @Test
    void aMissingQueryParameterIsA400ThatNamesIt() throws Exception {
        assertError(get("/posts/nearby").param("lng", "78.4"), 400, "Lat is required");
    }

    @Test
    void anUnsupportedContentTypeIsA415() throws Exception {
        assertError(post("/posts").header("Authorization", auth).contentType(MediaType.TEXT_PLAIN).content("hi"),
                415, "This content type isn't supported");
    }

    @Test
    void existingErrorsKeepTheirStatusAndShape() throws Exception {
        assertError(get("/profile/not-a-uuid"), 400, null);
        assertError(get("/profile/" + UUID.randomUUID()), 404, "Candidate not found");
        assertError(get("/posts/mine"), 401, "Authentication is required to access this resource");
        assertError(get("/enterprise/talent/search").header("Authorization", auth), 403, "Access denied");
        assertError(post("/posts").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content("{bad"),
                400, "One of the fields you entered isn't in a valid format");
    }

    @Test
    void fieldValidationErrorsStillCarryTheFieldMap() throws Exception {
        MvcResult result = mvc.perform(post("/auth/signin").contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("success").asBoolean(true)).isFalse();
        assertThat(body.path("data").isObject()).isTrue();
    }

    @Test
    void theServletErrorPageUsesTheSameShape() throws Exception {
        assertError(get("/error").with(r -> {
            r.setDispatcherType(DispatcherType.ERROR);
            r.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);
            return r;
        }), 500, "Something went wrong on our end. Please try again.");
        assertError(get("/error").with(r -> {
            r.setDispatcherType(DispatcherType.ERROR);
            r.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 429);
            return r;
        }), 429, "Too many requests. Please wait a moment and try again.");
    }

    @Test
    void databaseFailuresAre500sThatDoNotLeakSql() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleDataAccess(
                new DataAccessResourceFailureException("select password_hash from arena_users"));
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().message()).doesNotContain("arena_users");
    }

    @Test
    void aLockConflictIsARetryable409() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleConcurrency(
                new PessimisticLockingFailureException("could not obtain lock on row in relation arena_posts"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).doesNotContain("arena_posts");
    }

    @Test
    void anOversizedUploadIsA413() {
        assertThat(handler.handleUploadTooLarge(new MaxUploadSizeExceededException(10)).getStatusCode())
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    void aResponseStatusExceptionKeepsItsStatusAndReason() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleSpringRequestError(new ResponseStatusException(HttpStatus.CONFLICT, "Already done"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("Already done");
    }

    private void assertError(RequestBuilder request, int status, String message) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).as("status").isEqualTo(status);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        List<String> keys = new ArrayList<>();
        body.fieldNames().forEachRemaining(keys::add);
        assertThat(keys).as("error body keys").containsExactly("success", "message");
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("message").asText()).isNotBlank();
        if (message != null) assertThat(body.get("message").asText()).isEqualTo(message);
    }
}
