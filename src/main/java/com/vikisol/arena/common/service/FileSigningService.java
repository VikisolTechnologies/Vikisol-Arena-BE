package com.vikisol.arena.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * PRODUCTION-CHECKLIST.md's "serve via signed, expiring URLs" (secure resume upload section).
 * `GET /files/**` stays unauthenticated at the Spring Security level (a direct browser
 * navigation / `<img src>` can't attach a Bearer token) - proof-of-authorization instead comes
 * from possessing a valid, time-limited HMAC over the file's path, minted fresh every time a
 * profile/response containing a file URL is serialized (see CandidateProfileMapper). A leaked
 * or logged URL stops working after expiry instead of granting permanent access.
 */
@Service
@Slf4j
public class FileSigningService {

    private static final long EXPIRY_SECONDS = 600; // 10 minutes - long enough for a page load + resume viewing

    @Value("${app.file-signing.secret:${app.jwt.secret:local-dev-only-arena-secret-do-not-use-in-any-deployed-environment-change-me}}")
    private String secret;

    /** Appends `?exp=...&sig=...` to an already-built `/files/...` URL. No-op on blank input. */
    public String sign(String url) {
        if (url == null || url.isBlank()) return url;
        String path = extractPath(url);
        long exp = Instant.now().getEpochSecond() + EXPIRY_SECONDS;
        String sig = hmac(path, exp);
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + "exp=" + exp + "&sig=" + sig;
    }

    /** `path` is the request path actually served, e.g. `/files/candidate-cv/{id}/cv/{uuid}.pdf`. */
    public boolean verify(String path, long exp, String sig) {
        if (Instant.now().getEpochSecond() > exp) return false;
        String expected = hmac(path, exp);
        // Constant-time compare - a signature check is exactly the kind of thing a timing
        // side-channel can leak, same reasoning as password-hash comparisons.
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), sig.getBytes(StandardCharsets.UTF_8));
    }

    private String extractPath(String url) {
        int schemeEnd = url.indexOf("://");
        if (schemeEnd < 0) return url.split("\\?")[0];
        int pathStart = url.indexOf('/', schemeEnd + 3);
        String withoutHost = pathStart < 0 ? "" : url.substring(pathStart);
        return withoutHost.split("\\?")[0];
    }

    private String hmac(String path, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal((path + ":" + exp).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            log.error("File URL signing failed", e);
            throw new IllegalStateException("Could not sign file URL", e);
        }
    }
}
