package com.vikisol.arena.enterprise;

import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.enterprise.entity.CompanySize;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import com.vikisol.arena.enterprise.service.TalentSearchService;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.entity.ConsentSettings;
import com.vikisol.arena.profile.entity.Industry;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import com.vikisol.arena.schema.EmbeddedPostgresAppTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// ARCHITECT-REVIEW-BE-1 SHOULD-FIX: confirms TalentSearchService.unlock's row lock
// (EnterpriseProfileRepository.findByIdForUpdate) actually closes the cross-candidate
// credit-balance race - two concurrent unlocks against the same tenant with exactly one credit
// left must never both succeed.
//
// This test caught a real bug: the lock query DID block the second caller correctly, but
// requireEnterprise() earlier in the same method already loaded the same row (unlocked) into
// this transaction's Hibernate session, so the unblocked caller's entity still held the stale
// field values it read before ever waiting - both callers computed "0 + 1" independently and
// the second write clobbered the first. Fixed by an explicit entityManager.refresh() under the
// lock (see TalentSearchService.unlock's comment).
class UnlockCreditRaceTest extends EmbeddedPostgresAppTest {

    @Autowired UserRepository users;
    @Autowired EnterpriseProfileRepository enterprises;
    @Autowired CandidateProfileRepository profiles;
    @Autowired TalentSearchService talentSearchService;
    @Autowired PlatformTransactionManager transactions;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void onlyOneOfTwoConcurrentUnlocksSpendsTheLastCredit() throws Exception {
        var tx = new TransactionTemplate(transactions);
        record Ids(UUID enterpriseUserId, UUID candidateA, UUID candidateB) {}
        Ids ids = tx.execute(status -> {
            User recruiter = users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Recruiter")
                    .role(Role.COMPANY_ADMIN).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
            EnterpriseProfile tenant = enterprises.save(EnterpriseProfile.builder().user(recruiter).companyName("Acme").logoEmoji("A")
                    .industry(Industry.DESIGN).size(CompanySize.S_11_50).unlockCreditsTotal(1).unlockCreditsUsed(0).build());
            // Deliberately not common test names like "Asha"/"Designer" - this test commits real,
            // non-rolled-back rows (TransactionTemplate, not the class's usual @Transactional
            // rollback), so a collision with another test's search term would leak across tests
            // sharing this JVM's one embedded Postgres instance.
            CandidateProfile a = candidate("UnlockRaceCandidateAlpha" + UUID.randomUUID());
            CandidateProfile b = candidate("UnlockRaceCandidateBeta" + UUID.randomUUID());
            return new Ids(recruiter.getId(), a.getUser().getId(), b.getUser().getId());
        });

        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> unlockAfter(gate, ids.enterpriseUserId(), ids.candidateA()));
            var second = pool.submit(() -> unlockAfter(gate, ids.enterpriseUserId(), ids.candidateB()));
            gate.countDown();
            boolean firstOk = first.get(15, TimeUnit.SECONDS);
            boolean secondOk = second.get(15, TimeUnit.SECONDS);
            assertThat(firstOk ^ secondOk).as("exactly one of the two unlocks should succeed").isTrue();

            try {
                tx.executeWithoutResult(status -> {
                    EnterpriseProfile tenant = enterprises.findByUserId(ids.enterpriseUserId()).orElseThrow();
                    assertThat(tenant.getUnlockCreditsUsed()).isEqualTo(1);
                });
            } finally {
                // This test uses real, committed transactions (TransactionTemplate, not the
                // class's usual @Transactional rollback) to get genuine concurrency - clean up
                // afterward so this tenant doesn't linger in the shared embedded-Postgres instance
                // and get swept into some other test's global (not name-filtered) company count,
                // e.g. the legacy-verification grandfather list.
                tx.executeWithoutResult(status -> {
                    UUID tenantId = enterprises.findByUserId(ids.enterpriseUserId()).orElseThrow().getId();
                    jdbc.update("delete from arena_credit_ledger where tenant_id = ?", tenantId);
                    jdbc.update("delete from arena_unlocked_candidates where enterprise_id = ?", tenantId);
                    jdbc.update("delete from arena_audit_events where tenant_id = ?", tenantId);
                    jdbc.update("delete from arena_enterprise_profiles where id = ?", tenantId);
                });
            }
        }
    }

    private boolean unlockAfter(CountDownLatch gate, UUID enterpriseUserId, UUID candidateUserId) throws InterruptedException {
        gate.await();
        CandidateProfile candidate = profiles.findByUserId(candidateUserId).orElseThrow();
        try {
            talentSearchService.unlock(enterpriseUserId, candidate.getId());
            return true;
        } catch (com.vikisol.arena.common.exception.BadRequestException outOfCredits) {
            return false;
        }
    }

    private CandidateProfile candidate(String name) {
        User u = users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name(name)
                .role(Role.TALENT).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
        return profiles.save(CandidateProfile.builder().user(u).name(name).avatarEmoji("*").title("Unlock race test profile")
                .industry(Industry.DESIGN).location("Hyderabad").remote(false).consent(new ConsentSettings(false, true)).build());
    }
}
