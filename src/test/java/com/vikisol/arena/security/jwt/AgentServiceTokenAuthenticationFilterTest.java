package com.vikisol.arena.security.jwt;

import com.vikisol.arena.agent.client.AgentServiceTokenVerifier;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// M7 (approval-controlled write tools) - the security-critical scope-enforcement behavior: a
// valid, correctly-scoped service token authenticates the named real user for the one endpoint
// its scope actually grants; anything else (wrong scope, unknown endpoint, unknown user,
// unconfigured verifier, no token at all) must NOT populate the security context.
class AgentServiceTokenAuthenticationFilterTest {

    private final AgentServiceTokenVerifier verifier = mock(AgentServiceTokenVerifier.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AgentServiceTokenAuthenticationFilter filter =
            new AgentServiceTokenAuthenticationFilter(verifier, userRepository);

    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final FilterChain chain = mock(FilterChain.class);

    @BeforeEach
    void setUp() {
        when(verifier.isConfigured()).thenReturn(true);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private User fakeUser(UUID id) {
        User user = new User();
        user.setId(id);
        user.setEmail("talent@example.com");
        user.setName("Test Talent");
        user.setPasswordHash("irrelevant");
        user.setRole(Role.TALENT);
        return user;
    }

    @Test
    void authenticatesTheRealUserWhenTokenScopeMatchesTheRequestedEndpoint() throws Exception {
        UUID userId = UUID.randomUUID();
        when(request.getHeader("Authorization")).thenReturn("Bearer a-real-looking-token");
        when(request.getMethod()).thenReturn("POST");
        when(request.getServletPath()).thenReturn("/applications");
        when(verifier.verify("a-real-looking-token"))
                .thenReturn(new AgentServiceTokenVerifier.VerifiedClaims(userId, "TALENT", List.of("arena.applyToJob")));
        when(userRepository.findById(userId)).thenReturn(Optional.of(fakeUser(userId)));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("talent@example.com");
        verify(chain).doFilter(request, response);
    }

    @Test
    void doesNotAuthenticateWhenTokenScopeDoesNotCoverTheRequestedEndpoint() throws Exception {
        UUID userId = UUID.randomUUID();
        when(request.getHeader("Authorization")).thenReturn("Bearer a-real-looking-token");
        when(request.getMethod()).thenReturn("POST");
        when(request.getServletPath()).thenReturn("/applications");
        // Scoped only for reading jobs, not for applying - must not authenticate this request.
        when(verifier.verify("a-real-looking-token"))
                .thenReturn(new AgentServiceTokenVerifier.VerifiedClaims(userId, "TALENT", List.of("arena.searchJobs")));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userRepository, never()).findById(any());
        verify(chain).doFilter(request, response);
    }

    @Test
    void doesNotAuthenticateForAnEndpointWithNoScopeMappingAtAll() throws Exception {
        UUID userId = UUID.randomUUID();
        when(request.getHeader("Authorization")).thenReturn("Bearer a-real-looking-token");
        when(request.getMethod()).thenReturn("DELETE");
        when(request.getServletPath()).thenReturn("/applications/some-id");
        when(verifier.verify("a-real-looking-token"))
                .thenReturn(new AgentServiceTokenVerifier.VerifiedClaims(userId, "TALENT", List.of("arena.applyToJob")));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void fallsThroughSilentlyWhenTheTokenIsNotAValidServiceTokenAtAll() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer a-normal-arena-session-jwt");
        when(verifier.verify("a-normal-arena-session-jwt"))
                .thenThrow(new AgentServiceTokenVerifier.AgentServiceTokenInvalidException("wrong secret"));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void doesNothingWhenNoAuthorizationHeaderIsPresent() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(verifier);
    }

    @Test
    void doesNotAuthenticateWhenTheNamedUserNoLongerExists() throws Exception {
        UUID userId = UUID.randomUUID();
        when(request.getHeader("Authorization")).thenReturn("Bearer a-real-looking-token");
        when(request.getMethod()).thenReturn("POST");
        when(request.getServletPath()).thenReturn("/applications");
        when(verifier.verify("a-real-looking-token"))
                .thenReturn(new AgentServiceTokenVerifier.VerifiedClaims(userId, "TALENT", List.of("arena.applyToJob")));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
