package com.vikisol.arena.schema;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

/**
 * Base for full-application tests (real services, real Postgres) - for behaviour that lives in
 * the service layer, like who can see an anonymous author. Each test rolls back.
 */
@SpringBootTest
@Transactional
public abstract class EmbeddedPostgresAppTest {

    private static final EmbeddedPostgres POSTGRES = start();

    private static EmbeddedPostgres start() {
        try {
            EmbeddedPostgres pg = EmbeddedPostgres.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    pg.close();
                } catch (IOException ignored) {
                }
            }));
            return pg;
        } catch (IOException e) {
            throw new IllegalStateException("Could not start embedded Postgres", e);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        // No demo seeding, bootstrap seeding or Cloudinary in tests.
        registry.add("app.seed.enabled", () -> "false");
        registry.add("app.demo-content.enabled", () -> "false");
    }
}
