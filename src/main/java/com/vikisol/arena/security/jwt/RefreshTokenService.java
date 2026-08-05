package com.vikisol.arena.security.jwt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

/**
 * Opaque, rotating refresh tokens with reuse detection (RFC 9700 / BCP 240 §2.2.2 - see
 * DECISIONS.md). Redis-backed, not a JWT: an opaque random token can't be inspected/forged
 * client-side and is trivial to revoke server-side by deleting its key, which a self-contained
 * JWT refresh token cannot do without its own denylist anyway - simpler to just not use a JWT
 * here at all.
 *
 * Rotation: every successful refresh issues a brand-new token and invalidates the presented one.
 * Reuse detection: an already-rotated-away token is kept as a short-lived tombstone; if it's
 * presented again (the signature of a stolen-and-replayed token, since a legitimate client would
 * only ever present the LATEST token), every other live refresh token for that user is revoked,
 * forcing re-login everywhere rather than silently trusting the request.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String LIVE_PREFIX = "refresh:live:";
    private static final String USED_PREFIX = "refresh:used:";
    private static final String USER_SET_PREFIX = "refresh:user:";
    private static final Duration REUSE_TOMBSTONE_TTL = Duration.ofHours(1);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    public String issue(UUID userId) {
        String token = generateOpaqueToken();
        String hash = hash(token);
        Duration ttl = Duration.ofMillis(refreshExpirationMs);
        redis.opsForValue().set(LIVE_PREFIX + hash, userId.toString(), ttl);
        redis.opsForSet().add(USER_SET_PREFIX + userId, hash);
        redis.expire(USER_SET_PREFIX + userId, ttl);
        return token;
    }

    /** Validates + rotates a presented refresh token, returning the owning user id and a new
     * token. Throws BadCredentialsException for an invalid/expired/reused token - callers should
     * treat that as "log the user out," not retry. */
    public Result rotate(String presentedToken) {
        String hash = hash(presentedToken);
        String userId = redis.opsForValue().get(LIVE_PREFIX + hash);

        if (userId == null) {
            String reusedBy = redis.opsForValue().get(USED_PREFIX + hash);
            if (reusedBy != null) {
                log.warn("Refresh token reuse detected for user {} - revoking all sessions", reusedBy);
                revokeAllForUser(UUID.fromString(reusedBy));
            }
            throw new BadCredentialsException("Refresh token is invalid or expired");
        }

        UUID uid = UUID.fromString(userId);
        redis.delete(LIVE_PREFIX + hash);
        redis.opsForSet().remove(USER_SET_PREFIX + uid, hash);
        redis.opsForValue().set(USED_PREFIX + hash, userId, REUSE_TOMBSTONE_TTL);

        String newToken = issue(uid);
        return new Result(uid, newToken);
    }

    public void revokeAllForUser(UUID userId) {
        String setKey = USER_SET_PREFIX + userId;
        Set<String> hashes = redis.opsForSet().members(setKey);
        if (hashes != null) {
            hashes.forEach(h -> redis.delete(LIVE_PREFIX + h));
        }
        redis.delete(setKey);
    }

    public void revoke(String presentedToken) {
        redis.delete(LIVE_PREFIX + hash(presentedToken));
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record Result(UUID userId, String token) {
    }
}
