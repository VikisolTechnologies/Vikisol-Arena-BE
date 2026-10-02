package com.vikisol.arena.seed;

import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.entity.DemoSeedingContext;
import com.vikisol.arena.schema.EmbeddedPostgresAppTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// B11 item 2: every entity DataSeeder inserts must carry demo_content=true. DataSeeder itself is
// disabled in tests (app.seed.enabled=false - see EmbeddedPostgresAppTest), so this exercises the
// actual mechanism (BaseEntity's @PrePersist hook reading DemoSeedingContext) directly, the same
// way DataSeeder.run() brackets its own work.
class DemoSeedingContextTest extends EmbeddedPostgresAppTest {

    @Autowired UserRepository users;

    @Test
    void anEntityInsertedWhileActiveIsTaggedDemoContent() {
        DemoSeedingContext.begin();
        User tagged;
        try {
            tagged = users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Seeded")
                    .role(Role.TALENT).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
        } finally {
            DemoSeedingContext.end();
        }
        assertThat(tagged.isDemoContent()).isTrue();

        User real = users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Real")
                .role(Role.TALENT).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
        assertThat(real.isDemoContent()).isFalse();
    }

    @Test
    void endAlwaysClearsTheFlagEvenAfterAnException() {
        assertThat(DemoSeedingContext.isActive()).isFalse();
        DemoSeedingContext.begin();
        try {
            throw new RuntimeException("simulated failure mid-seed");
        } catch (RuntimeException ignored) {
        } finally {
            DemoSeedingContext.end();
        }
        assertThat(DemoSeedingContext.isActive()).isFalse();
    }
}
