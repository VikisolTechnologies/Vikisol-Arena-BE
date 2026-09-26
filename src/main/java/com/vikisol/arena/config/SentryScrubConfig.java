package com.vikisol.arena.config;

import io.sentry.SentryOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SentryScrubConfig {

    @Bean
    public SentryOptions.BeforeSendCallback sentryBeforeSend() {
        return (event, hint) -> SentryScrub.scrub(event);
    }
}
