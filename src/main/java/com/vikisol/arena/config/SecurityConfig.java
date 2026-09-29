package com.vikisol.arena.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.dto.PageLimits;
import com.vikisol.arena.security.jwt.AgentServiceTokenAuthenticationFilter;
import com.vikisol.arena.security.jwt.JwtAuthenticationEntryPoint;
import com.vikisol.arena.security.jwt.JwtAuthenticationFilter;
import com.vikisol.arena.security.mfa.PlatformAdminMfaFilter;
import com.vikisol.arena.security.ratelimit.RateLimitFilter;
import com.vikisol.arena.security.service.CustomUserDetailsService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AgentServiceTokenAuthenticationFilter agentServiceTokenAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final PlatformAdminMfaFilter platformAdminMfaFilter;
    private final CustomUserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(request -> {
                    var config = new org.springframework.web.cors.CorsConfiguration();
                    config.setAllowCredentials(true);
                    config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
                    config.addAllowedHeader("*");
                    config.addAllowedMethod("*");
                    // PageLimits' paging headers on the bare-array list endpoints.
                    config.setExposedHeaders(List.of(PageLimits.TOTAL_COUNT_HEADER, PageLimits.HAS_MORE_HEADER));
                    return config;
                }))
                .csrf(csrf -> csrf.disable())
                // PRODUCTION-CHECKLIST.md security headers. X-Content-Type-Options: nosniff and
                // X-Frame-Options: DENY are Spring Security defaults already; CSP is not, and is
                // added explicitly. This is a pure JSON API (Swagger UI is disabled outside dev -
                // see application.yml's springdoc.api-docs.enabled), so a blanket-deny default
                // is safe: there's no first-party HTML/JS for the API itself to serve. HSTS is
                // conditional on the request already being HTTPS (Spring's own behavior) - see
                // server.forward-headers-strategy in application.yml for why that's still
                // correct behind Railway's TLS-terminating proxy.
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                )
                // 403s decided by the filter chain use the same ApiResponse body as every other
                // error (the default handler sent an empty body through /error).
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler((request, response, denied) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(objectMapper.writeValueAsString(
                                    new ApiResponse<>(false, "Access denied", null)));
                        }))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // The /error dispatch only renders the status of a request that already
                        // failed (ApiErrorController). Guarding it again turned a guest's 500 into
                        // a misleading 401.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // Was a blanket "/auth/**".permitAll() - found live-testing the
                        // signout/denylist flow that this silently let an unauthenticated (or
                        // just-revoked) caller reach /auth/me with a null Authentication,
                        // NPE-ing instead of 401ing (see DECISIONS.md). Enumerate only the
                        // genuinely-public auth endpoints explicitly instead.
                        .requestMatchers(HttpMethod.POST, "/auth/signup", "/auth/signin", "/auth/refresh",
                                "/auth/signout", "/auth/2fa/verify", "/auth/invitations/accept",
                                // Google/phone sign-in/signup - the whole point is a signed-out
                                // visitor can reach them. change-password/change-email stay off
                                // this list on purpose (they fall to .anyRequest().authenticated()).
                                "/auth/google", "/auth/phone/signin/request-otp", "/auth/phone/signin/verify-otp",
                                "/auth/phone/signup/request-otp", "/auth/phone/signup/verify-otp",
                                "/auth/email/signin/request-otp", "/auth/email/signin/verify-otp",
                                "/auth/forgot-password", "/auth/reset-password").permitAll()
                        .requestMatchers(HttpMethod.GET, "/auth/invitations/*").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/version").permitAll()
                        // Logged-out marketing homepage's real stats/featured-bid card
                        // (LandingController) - deliberately read-only and pre-aggregated so it
                        // can't be used to enumerate individual users/projects.
                        .requestMatchers(HttpMethod.GET, "/public/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/files/**").permitAll()
                        // ARENA-INVENTORY-FIXES.md FIX 1 - shared profile/company/discover links
                        // are the product's growth loop, so these three read-only surfaces must
                        // work logged-out. "/profile/me" is listed BEFORE the "/profile/*"
                        // wildcard deliberately: authorizeHttpRequests uses the first matching
                        // rule, and Ant's "*" doesn't cross "/" but WOULD still match the single
                        // "me" segment, so the specific rule has to come first or the wildcard
                        // would wrongly expose the caller's own full profile anonymously.
                        .requestMatchers(HttpMethod.GET, "/profile/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/profile/*").permitAll()
                        // "Enter as guest" - a visitor can browse the whole app (feed, jobs,
                        // projects, companies, map) read-only before ever signing up; only the
                        // actual write (post, join, apply, bid, follow, message) asks for an
                        // account, via SignInPrompt on the frontend. Every service method behind
                        // these GETs already treats a null viewingUserId as "anonymous" (see
                        // PostService/FeedAggregationService/ProjectService/CompanyService) -
                        // that null-tolerance was already there for shared-link support (FIX 1 /
                        // G9 below); this just opens the same door to the main browse surfaces.
                        .requestMatchers(HttpMethod.GET, "/companies", "/companies/*", "/companies/*/jobs").permitAll()
                        .requestMatchers(HttpMethod.GET, "/jobs").permitAll()
                        .requestMatchers(HttpMethod.GET, "/search").permitAll()
                        // Phase 2 (Discuss) - browsing communities and threads is open to guests;
                        // "/communities/mine" stays authenticated (it's listed first on purpose).
                        .requestMatchers(HttpMethod.GET, "/communities/mine").authenticated()
                        .requestMatchers(HttpMethod.GET, "/discuss/threads", "/communities", "/communities/*", "/communities/*/moderators").permitAll()
                        .requestMatchers(HttpMethod.GET, "/feed").permitAll()
                        .requestMatchers(HttpMethod.GET, "/marketplace/projects", "/marketplace/projects/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/posts/by-user/*").permitAll()
                        // ARENA-STABILIZE.md Phase 2, G9 - shared post links must work
                        // logged-out too. Same "specific-before-wildcard" ordering as above:
                        // "/posts/*" (single Ant segment) would also match the literal
                        // "/posts/mine"/"/posts/saved"/"/posts/joined" GET endpoints - list
                        // those explicitly first so the wildcard below only ever reaches an
                        // actual post id. "/posts/joined" is the caller's own joins; leaving
                        // it on the permitAll wildcard reached getJoined with a null principal.
                        // feed/trending/nearby used to be forced authenticated here too, but
                        // that's what blocked guest browsing of Home/Map - each already degrades
                        // to anonymous-safe defaults (no follow-boost, no "mine" flags), same as
                        // every other permitAll GET on this list.
                        .requestMatchers(HttpMethod.GET, "/posts/mine", "/posts/saved", "/posts/joined").authenticated()
                        .requestMatchers(HttpMethod.GET, "/posts/*", "/posts/*/comments").permitAll()
                        // An activity's page (details, questions, spots, waitlist size) is as
                        // public as the post itself; "/activities/kinds" is the form catalogue.
                        .requestMatchers(HttpMethod.GET, "/activities/*").permitAll()
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                // M7: runs before JwtAuthenticationFilter - a normal Arena session JWT simply
                // fails verification here (different secret, see the filter's own class doc) and
                // falls through untouched, so ordering relative to JwtAuthenticationFilter has no
                // effect on normal requests.
                .addFilterBefore(agentServiceTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // After JWT auth so an authenticated bucket can key by user id, not just IP -
                // see RateLimitFilter's own comment.
                .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class)
                .addFilterAfter(platformAdminMfaFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public FilterRegistrationBean<PlatformAdminMfaFilter> platformAdminMfaFilterRegistration(PlatformAdminMfaFilter filter) {
        FilterRegistrationBean<PlatformAdminMfaFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
