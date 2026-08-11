package com.vikisol.arena.common.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

// ARENA-STABILIZE.md Phase 0.2 - a public, phone-checkable "is this actually the latest code"
// endpoint. build-info.properties is written into the image by the Dockerfile at build time
// (commit + build time), read once at startup since it never changes for the life of the
// container. "unknown" only happens running outside the Docker image (e.g. `mvn spring-boot:run`
// locally), where that file is never generated.
@RestController
public class VersionController {

    private final Map<String, String> info;

    public VersionController() {
        Properties props = new Properties();
        try (var in = Files.newInputStream(Path.of("build-info.properties"))) {
            props.load(in);
        } catch (IOException ignored) {
            // Not running from the Docker image - fields stay "unknown" below.
        }
        Map<String, String> m = new LinkedHashMap<>();
        m.put("commit", props.getProperty("commit", "unknown"));
        m.put("builtAt", props.getProperty("builtAt", "unknown"));
        this.info = m;
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/version")
    public Map<String, String> version() {
        return info;
    }
}
