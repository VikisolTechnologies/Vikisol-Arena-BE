package com.vikisol.arena;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling added for ARENA-V2-PRODUCT-ARCHITECTURE.md Phase B's PostLifecycleScheduler
// (activity reminders + auto-expiry) - the first scheduled-job infrastructure in this codebase.
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
public class ArenaApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(ArenaApiApplication.class, args);
    }
}
