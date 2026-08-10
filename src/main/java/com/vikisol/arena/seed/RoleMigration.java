package com.vikisol.arena.seed;

import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.util.HandleGenerator;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.entity.Membership;
import com.vikisol.arena.enterprise.entity.MembershipStatus;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import com.vikisol.arena.enterprise.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time, idempotent migration for the ARENA-ENTERPRISE-SUITE.md role model (see
 * DECISIONS.md): retires Role.ENTERPRISE in favor of RECRUITER/COMPANY_ADMIN/HIRING_MANAGER/
 * PLATFORM_ADMIN, and introduces Membership as the real user<->tenant link. Runs before
 * DataSeeder (which no-ops on an already-seeded database, so it would never reach a migration
 * step placed there instead) and before anything else tries to load a User row with the
 * now-nonexistent 'ENTERPRISE' enum value.
 *
 * The rename is raw SQL (JdbcTemplate), not JPA - loading a User entity with role='ENTERPRISE'
 * through Hibernate's @Enumerated(EnumType.STRING) would itself throw, so this has to happen
 * below the ORM. The Membership backfill runs after, once every row is a real enum value again,
 * so it's safe to use JPA repositories from there on.
 *
 * No Flyway/Liquibase in this project (ddl-auto: update, per application.yml) - this is the
 * established pattern for one-off data fixes here already (see DataSeeder's own use of
 * JdbcTemplate for backdating timestamps).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RoleMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final EnterpriseProfileRepository enterpriseProfileRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Hibernate 6 auto-generates a CHECK constraint listing the enum's allowed values the
        // first time it creates an @Enumerated(STRING) column, but ddl-auto:update never revisits
        // it when the Java enum's value set changes later - it's still stuck enforcing the old
        // TALENT/ENTERPRISE pair and rejects every new role value, including this migration's own
        // rename target. Drop it; nothing here relies on the DB enforcing role membership, the
        // application layer already does (Role.fromWireValue, @PreAuthorize).
        jdbcTemplate.execute("ALTER TABLE arena_users DROP CONSTRAINT IF EXISTS arena_users_role_check");

        int renamed = jdbcTemplate.update("UPDATE arena_users SET role = 'COMPANY_ADMIN' WHERE role = 'ENTERPRISE'");
        if (renamed > 0) {
            log.info("RoleMigration: renamed {} ENTERPRISE user(s) to COMPANY_ADMIN", renamed);
        }
        backfillMemberships();
        backfillDemoAccounts();
    }

    private void backfillMemberships() {
        int created = 0;
        for (EnterpriseProfile tenant : enterpriseProfileRepository.findAll()) {
            if (tenant.getUser() == null) continue;
            if (membershipRepository.findByUserId(tenant.getUser().getId()).isPresent()) continue;
            membershipRepository.save(Membership.builder()
                    .user(tenant.getUser()).tenant(tenant).status(MembershipStatus.ACTIVE)
                    .joinedAt(tenant.getUser().getCreatedAt())
                    .build());
            created++;
        }
        if (created > 0) {
            log.info("RoleMigration: backfilled {} Membership row(s) for pre-existing tenant owners", created);
        }
    }

    // DataSeeder's own seedDemoRecruiterAndHiringManager()/seedPlatformAdmin() only ever run
    // against a genuinely fresh database (DataSeeder no-ops the instant any user exists) - this
    // covers the far more common case of a database that was already seeded before this suite's
    // demo accounts existed. Same idempotent-by-lookup pattern as backfillMemberships().
    private void backfillDemoAccounts() {
        userRepository.findByEmailIgnoreCase(DataSeeder.DEMO_ENTERPRISE_EMAIL).ifPresent(admin -> {
            EnterpriseProfile tenant = enterpriseProfileRepository.findByUserId(admin.getId()).orElse(null);
            if (tenant == null) return;

            if (userRepository.findByEmailIgnoreCase(DataSeeder.DEMO_RECRUITER_EMAIL).isEmpty()) {
                User recruiter = userRepository.save(User.builder()
                        .email(DataSeeder.DEMO_RECRUITER_EMAIL).passwordHash(passwordEncoder.encode(DataSeeder.DEMO_PASSWORD))
                        .name("Priyanka Rao").role(Role.RECRUITER)
                        .handle(HandleGenerator.generate("Priyanka Rao", userRepository::existsByHandle)).build());
                membershipRepository.save(Membership.builder()
                        .user(recruiter).tenant(tenant).status(MembershipStatus.ACTIVE)
                        .invitedBy(admin).joinedAt(recruiter.getCreatedAt()).build());
                log.info("RoleMigration: seeded demo recruiter {}", DataSeeder.DEMO_RECRUITER_EMAIL);
            }

            if (userRepository.findByEmailIgnoreCase(DataSeeder.DEMO_HIRING_MANAGER_EMAIL).isEmpty()) {
                User hiringManager = userRepository.save(User.builder()
                        .email(DataSeeder.DEMO_HIRING_MANAGER_EMAIL).passwordHash(passwordEncoder.encode(DataSeeder.DEMO_PASSWORD))
                        .name("Karthik Iyer").role(Role.HIRING_MANAGER)
                        .handle(HandleGenerator.generate("Karthik Iyer", userRepository::existsByHandle)).build());
                membershipRepository.save(Membership.builder()
                        .user(hiringManager).tenant(tenant).status(MembershipStatus.ACTIVE)
                        .invitedBy(admin).joinedAt(hiringManager.getCreatedAt()).build());
                log.info("RoleMigration: seeded demo hiring manager {}", DataSeeder.DEMO_HIRING_MANAGER_EMAIL);
            }
        });

        if (userRepository.findByEmailIgnoreCase(DataSeeder.PLATFORM_ADMIN_EMAIL).isEmpty()) {
            userRepository.save(User.builder()
                    .email(DataSeeder.PLATFORM_ADMIN_EMAIL).passwordHash(passwordEncoder.encode(DataSeeder.DEMO_PASSWORD))
                    .name("Vikisol Platform Admin").role(Role.PLATFORM_ADMIN)
                    .handle(HandleGenerator.generate("Vikisol Platform Admin", userRepository::existsByHandle)).build());
            log.info("RoleMigration: seeded platform admin {}", DataSeeder.PLATFORM_ADMIN_EMAIL);
        }
    }
}
