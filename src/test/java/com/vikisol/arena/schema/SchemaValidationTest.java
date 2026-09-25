package com.vikisol.arena.schema;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs every Flyway migration against a real (embedded) Postgres, then lets Hibernate validate
 * every @Entity against the result (ddl-auto: validate from application.yml). If a migration
 * and an entity disagree, this fails here instead of at Railway startup.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SchemaValidationTest {

    private static EmbeddedPostgres postgres;

    @BeforeAll
    static void start() throws IOException {
        postgres = EmbeddedPostgres.start();
    }

    @AfterAll
    static void stop() throws IOException {
        if (postgres != null) postgres.close();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void migrationsAndEntitiesAgree() {
        // Reaching here means Flyway migrated cleanly and Hibernate's validate pass succeeded.
        assertThat(entityManagerFactory.getMetamodel().getEntities()).isNotEmpty();
    }
}
