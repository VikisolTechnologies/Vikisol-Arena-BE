package com.vikisol.arena.seed;

import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.util.HandleGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Marks the original bootstrap rows as demo content, retires the published shared password,
 * and disables the demo platform-admin address. A real platform admin is created only when
 * PLATFORM_ADMIN_EMAIL and PLATFORM_ADMIN_PASSWORD are both set, and never on the retired
 * demo address. Nothing here is written to the log.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
public class DemoAccountLockdown implements ApplicationRunner {

    private static final String DEMO_USER_SQL = """
            SELECT email FROM arena_users
            WHERE lower(email) LIKE 'demo.%@vikisol.dev'
               OR lower(email) = 'admin@vikisol.dev'
               OR lower(email) LIKE 'candidate%@example.com'
               OR lower(email) LIKE 'hr@%.example.com'
               OR lower(email) LIKE '%@demo.arena.test'
            """;

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DemoPassword demoPassword;

    @Value("${app.platform-admin.email:}")
    private String platformAdminEmail;

    @Value("${app.platform-admin.password:}")
    private String platformAdminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        retireDemoLogins();
        markRelatedDemoRows();
        renameSeededBrandCompanies();
        bootstrapPlatformAdmin();
    }

    private void retireDemoLogins() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS arena_lockdown_state (
                    id text PRIMARY KEY,
                    done_at timestamptz NOT NULL
                )
                """);
        Integer already = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM arena_lockdown_state WHERE id = 'demo-password-retired'", Integer.class);
        List<String> emails = jdbcTemplate.queryForList(DEMO_USER_SQL, String.class);
        int rotated = 0;
        int disabled = 0;
        boolean rotatePasswords = already == null || already == 0;
        for (String email : emails) {
            User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
            if (user == null) continue;
            user.setDemoContent(true);
            if (rotatePasswords && demoPassword.matchesRetired(passwordEncoder, user.getPasswordHash())) {
                String replacement = demoPassword.configured().orElseGet(() -> UUID.randomUUID().toString());
                user.setPasswordHash(passwordEncoder.encode(replacement));
                rotated++;
            }
            if (email.equalsIgnoreCase(DataSeeder.PLATFORM_ADMIN_EMAIL) && user.getDeletedAt() == null) {
                user.setDeletedAt(Instant.now());
                disabled++;
            }
            userRepository.save(user);
        }
        if (rotatePasswords) {
            jdbcTemplate.update(
                    "INSERT INTO arena_lockdown_state (id, done_at) VALUES ('demo-password-retired', now()) ON CONFLICT (id) DO NOTHING");
        }
        if (!emails.isEmpty()) {
            log.info("Demo lockdown: marked {} seeded account(s), rotated {} published password(s), disabled {} demo platform admin",
                    emails.size(), rotated, disabled);
        }
    }

    private void markRelatedDemoRows() {
        jdbcTemplate.update("""
                UPDATE arena_candidate_profiles p SET demo_content = true
                FROM arena_users u WHERE p.user_id = u.id AND u.demo_content = true AND p.demo_content = false
                """);
        jdbcTemplate.update("""
                UPDATE arena_enterprise_profiles p SET demo_content = true
                FROM arena_users u WHERE p.user_id = u.id AND u.demo_content = true AND p.demo_content = false
                """);
        jdbcTemplate.update("""
                UPDATE arena_projects p SET demo_content = true
                FROM arena_users u WHERE p.posted_by_user_id = u.id AND u.demo_content = true AND p.demo_content = false
                """);
        jdbcTemplate.update("""
                UPDATE arena_posts p SET demo_content = true
                FROM arena_users u WHERE p.author_user_id = u.id AND u.demo_content = true AND p.demo_content = false
                """);
        jdbcTemplate.update("""
                UPDATE arena_bids b SET demo_content = true
                FROM arena_users u WHERE b.bidder_user_id = u.id AND u.demo_content = true AND b.demo_content = false
                """);
        jdbcTemplate.update("""
                UPDATE arena_bids b SET demo_content = true
                FROM arena_projects p WHERE b.project_id = p.id AND p.demo_content = true AND b.demo_content = false
                """);
        jdbcTemplate.update("""
                UPDATE arena_job_postings j SET demo_content = true
                FROM arena_enterprise_profiles e
                WHERE j.enterprise_id = e.id AND e.demo_content = true AND j.demo_content = false
                """);
    }

    private void renameSeededBrandCompanies() {
        jdbcTemplate.update("""
                UPDATE arena_enterprise_profiles SET company_name = CASE company_name
                    WHEN 'Techolution' THEN 'Northwind Desk'
                    WHEN 'Swiggy' THEN 'Harbour Route'
                    WHEN 'Microsoft' THEN 'Lumen Works'
                    WHEN 'Innova Solutions' THEN 'Paperkite Labs'
                    WHEN 'Paytm' THEN 'Mintline'
                    WHEN 'Zoho' THEN 'Cedar Ledger'
                    WHEN 'Freshworks' THEN 'Fieldnote'
                    WHEN 'Practo' THEN 'Clinic Lane'
                    WHEN 'Delhivery' THEN 'Parcel North'
                    WHEN 'Razorpay' THEN 'Clearstack'
                    ELSE company_name
                END
                WHERE demo_content = true
                  AND company_name IN (
                    'Techolution', 'Swiggy', 'Microsoft', 'Innova Solutions', 'Paytm',
                    'Zoho', 'Freshworks', 'Practo', 'Delhivery', 'Razorpay')
                """);
    }

    private void bootstrapPlatformAdmin() {
        String email = platformAdminEmail == null ? "" : platformAdminEmail.trim();
        String password = platformAdminPassword == null ? "" : platformAdminPassword;
        if (email.isBlank() || password.isBlank()) {
            log.info("Platform admin bootstrap skipped: PLATFORM_ADMIN_EMAIL or PLATFORM_ADMIN_PASSWORD is unset");
            return;
        }
        if (email.equalsIgnoreCase(DataSeeder.PLATFORM_ADMIN_EMAIL)) {
            log.warn("Refusing to bootstrap a platform admin on the retired demo address");
            return;
        }
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .name("Platform admin")
                .role(Role.PLATFORM_ADMIN)
                .handle(HandleGenerator.generate("Platform admin", userRepository::existsByHandle))
                .build());
        log.info("Created a platform admin account from the environment");
    }
}
