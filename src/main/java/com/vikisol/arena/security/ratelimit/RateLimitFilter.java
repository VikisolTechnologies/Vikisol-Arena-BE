package com.vikisol.arena.security.ratelimit;

import com.vikisol.arena.security.service.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * PRODUCTION-CHECKLIST.md's rate-limiting ask, minus the Bucket4j library itself: a fixed
 * 60-second window counted with a plain Redis INCR+EXPIRE (same StringRedisTemplate already
 * used by TokenDenylistService/RefreshTokenService) returns the same practical behavior - a
 * 429 with Retry-After once a caller exceeds their per-minute budget - without adding a second
 * Redis client/connection style (bucket4j-redis needs its own Lettuce/Jedis wiring, separate
 * from Spring Data Redis's StringRedisTemplate already in use everywhere else here). Placed
 * after JwtAuthenticationFilter in the chain so an authenticated bucket can key by user id,
 * not just IP.
 *
 * Fails OPEN on a Redis error (logs and lets the request through) - deliberately the opposite
 * tradeoff from TokenDenylistService/RefreshTokenService, where a Redis outage correctly blocks
 * auth entirely (see DECISIONS.md's "Redis is a hard dependency for sign-in" note). Here the
 * downside of failing closed (a transient Redis blip takes down 100% of the API, not just
 * abuse protection) is worse than the downside of failing open - found live when a local
 * Memurai RDB-persistence error put it into a write-refusing safety mode mid-session and every
 * single request started 401ing because this filter's uncaught exception was breaking the
 * whole chain.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redis;

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled;
    @Value("${app.rate-limit.auth-per-minute:10}")
    private int authPerMinute;
    @Value("${app.rate-limit.upload-per-minute:5}")
    private int uploadPerMinute;
    @Value("${app.rate-limit.unlock-per-minute:20}")
    private int unlockPerMinute;
    @Value("${app.rate-limit.messaging-per-minute:30}")
    private int messagingPerMinute;
    // ARENA-V2-PRODUCT-ARCHITECTURE.md §4 "rate limits on posting and joining" (Phase B).
    @Value("${app.rate-limit.post-creation-per-minute:5}")
    private int postCreationPerMinute;
    @Value("${app.rate-limit.join-request-per-minute:10}")
    private int joinRequestPerMinute;
    @Value("${app.rate-limit.default-per-minute:120}")
    private int defaultPerMinute;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        Bucket bucket = bucketFor(request);
        String key = "ratelimit:" + bucket.name() + ":" + identityFor(request);
        Long count;
        try {
            count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofMinutes(1));
            }
        } catch (Exception e) {
            log.warn("Rate limit check failed (Redis unavailable?) - letting the request through: {}", e.getMessage());
            chain.doFilter(request, response);
            return;
        }
        if (count != null && count > bucket.limit()) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.getWriter().write("{\"success\":false,\"message\":\"Too many requests. Please wait a moment and try again.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private record Bucket(String name, int limit) {}

    private Bucket bucketFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.contains("/auth/signin") || path.contains("/auth/signup") || path.contains("/auth/refresh")
                || path.contains("/auth/2fa/")) {
            return new Bucket("auth", authPerMinute);
        }
        if (path.endsWith("/cv") && "POST".equalsIgnoreCase(request.getMethod())) {
            return new Bucket("upload", uploadPerMinute);
        }
        if (path.endsWith("/unlock")) {
            return new Bucket("unlock", unlockPerMinute);
        }
        if (path.contains("/messages/")) {
            return new Bucket("messaging", messagingPerMinute);
        }
        if (path.endsWith("/joins") && "POST".equalsIgnoreCase(request.getMethod())) {
            return new Bucket("join-request", joinRequestPerMinute);
        }
        if (path.endsWith("/posts") && "POST".equalsIgnoreCase(request.getMethod())) {
            return new Bucket("post-creation", postCreationPerMinute);
        }
        return new Bucket("default", defaultPerMinute);
    }

    // Unauthenticated endpoints (sign-in/sign-up, before a principal exists) key by client IP -
    // request.getRemoteAddr() already reflects X-Forwarded-For correctly behind Railway's proxy
    // once server.forward-headers-strategy=framework is set (see application.yml). Authenticated
    // endpoints key by user id instead, since IP-based limiting alone would let one office/NAT
    // full of legitimate recruiters throttle each other.
    private String identityFor(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return "user:" + principal.getId();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
