package com.vikisol.arena.security.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

// Server-side revocation for the short-lived JWT access token (see DECISIONS.md - the checklist's
// "server-side session store or jti denylist" ask). Entries expire from Redis on their own once
// the underlying token would have expired anyway, so this never accumulates unbounded state.
@Service
@RequiredArgsConstructor
public class TokenDenylistService {

    private static final String KEY_PREFIX = "jwt:denylist:";

    private final StringRedisTemplate redis;

    public void denylist(String jti, Instant tokenExpiry) {
        Duration ttl = Duration.between(Instant.now(), tokenExpiry);
        if (ttl.isNegative() || ttl.isZero()) return; // already expired, nothing to deny
        redis.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
    }

    public boolean isDenylisted(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + jti));
    }
}
