package com.vikisol.arena.security.proxy;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class TrustedProxyStartupGuard {

    private final Environment environment;

    @Value("${app.proxy.secret:}")
    private String proxySecret;

    @Value("${app.proxy.required:true}")
    private boolean proxyRequired;

    @PostConstruct
    void verifyProxySecretConfigured() {
        if (environment.acceptsProfiles(Profiles.of("local")) || StringUtils.hasText(proxySecret)) {
            return;
        }
        if (proxyRequired) {
            throw new IllegalStateException("ARENA_PROXY_SECRET must be set outside the local profile, "
                    + "or app.proxy.required=false must be explicitly configured as a temporary escape hatch.");
        }
        log.warn("ARENA_PROXY_SECRET is blank while app.proxy.required=false outside the local profile; "
                + "trusted proxy enforcement is disabled and this must only be used as a temporary deployment escape hatch.");
    }
}
