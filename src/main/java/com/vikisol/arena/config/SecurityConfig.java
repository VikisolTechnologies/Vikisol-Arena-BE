package com.vikisol.arena.config;

import com.vikisol.arena.security.jwt.JwtAuthenticationEntryPoint;
import com.vikisol.arena.security.jwt.JwtAuthenticationFilter;
import com.vikisol.arena.security.ratelimit.RateLimitFilter;
import com.vikisol.arena.security.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final CustomUserDetailsService userDetailsService;

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
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Was a blanket "/auth/**".permitAll() - found live-testing the
                        // signout/denylist flow that this silently let an unauthenticated (or
                        // just-revoked) caller reach /auth/me with a null Authentication,
                        // NPE-ing instead of 401ing (see DECISIONS.md). Enumerate only the
                        // genuinely-public auth endpoints explicitly instead.
                        .requestMatchers(HttpMethod.POST, "/auth/signup", "/auth/signin", "/auth/refresh",
                                "/auth/signout", "/auth/2fa/verify", "/auth/invitations/accept").permitAll()
                        .requestMatchers(HttpMethod.GET, "/auth/invitations/*").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
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
                        .requestMatchers(HttpMethod.GET, "/companies/*", "/companies/*/jobs").permitAll()
                        .requestMatchers(HttpMethod.GET, "/jobs").permitAll()
                        .requestMatchers(HttpMethod.GET, "/posts/by-user/*").permitAll()
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // After JWT auth so an authenticated bucket can key by user id, not just IP -
                // see RateLimitFilter's own comment.
                .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);

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
}
