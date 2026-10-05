package com.vikisol.arena.config;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

final class CorsOriginPolicy {

    private CorsOriginPolicy() {
    }

    static List<String> allowedOrigins(String configuredOrigins, Environment environment) {
        if (StringUtils.hasText(configuredOrigins)) {
            return Arrays.stream(configuredOrigins.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        if (environment.acceptsProfiles(Profiles.of("local"))) {
            return List.of("http://localhost:3000", "http://localhost:3001");
        }
        return List.of();
    }
}
