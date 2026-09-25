package com.vikisol.arena.schema;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs every Flyway migration against a real (embedded) Postgres, then lets Hibernate validate
 * every @Entity against the result (ddl-auto: validate). If a migration and an entity disagree,
 * this fails here instead of at Railway startup.
 */
class SchemaValidationTest extends EmbeddedPostgresTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void migrationsAndEntitiesAgree() {
        // Reaching here means Flyway migrated cleanly and Hibernate's validate pass succeeded.
        assertThat(entityManagerFactory.getMetamodel().getEntities()).isNotEmpty();
    }
}
