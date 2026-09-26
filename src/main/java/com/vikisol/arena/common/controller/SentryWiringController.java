package com.vikisol.arena.common.controller;

import io.sentry.Sentry;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

// One-shot check that the deployed API SDK can deliver an event. Inactive unless
// SENTRY_WIRING_NONCE is set, and removed after that check.
@RestController
public class SentryWiringController {

    @PreAuthorize("permitAll()")
    @PostMapping("/sentry-wiring")
    public ResponseEntity<Map<String, Object>> check(
            @RequestHeader(value = "X-Sentry-Wiring", required = false) String given) {
        String expected = System.getenv("SENTRY_WIRING_NONCE");
        if (expected == null || expected.isBlank() || !expected.equals(given)) {
            return ResponseEntity.notFound().build();
        }
        var id = Sentry.captureException(new IllegalStateException("Arena api Sentry wiring check"));
        Sentry.flush(4000);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id.toString());
        return ResponseEntity.ok(body);
    }
}
