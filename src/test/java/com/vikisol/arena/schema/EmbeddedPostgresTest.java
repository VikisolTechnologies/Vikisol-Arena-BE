package com.vikisol.arena.schema;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;

/**
 * Base for JPA tests against a real Postgres: one embedded server for the whole test run, every
 * Flyway migration applied, Hibernate validating every entity. Each test method still rolls back
 * (DataJpaTest), so tests don't see each other's rows.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class EmbeddedPostgresTest {

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
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
