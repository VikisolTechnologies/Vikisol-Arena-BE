package com.vikisol.arena.seed;

import com.vikisol.arena.activity.service.ActivityService;
import com.vikisol.arena.applications.repository.ApplicationRepository;
import com.vikisol.arena.common.entity.DemoSeedingContext;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import com.vikisol.arena.enterprise.repository.MembershipRepository;
import com.vikisol.arena.enterprise.repository.ShortlistEntryRepository;
import com.vikisol.arena.enterprise.repository.UnlockedCandidateRepository;
import com.vikisol.arena.follows.repository.FollowRepository;
import com.vikisol.arena.interviews.repository.InterviewRepository;
import com.vikisol.arena.jobs.repository.JobPostingRepository;
import com.vikisol.arena.marketplace.repository.BidRepository;
import com.vikisol.arena.marketplace.repository.MilestoneRepository;
import com.vikisol.arena.marketplace.repository.ProjectRepository;
import com.vikisol.arena.matching.ScoringService;
import com.vikisol.arena.notifications.service.NotificationService;
import com.vikisol.arena.posts.repository.PostJoinRequestRepository;
import com.vikisol.arena.posts.repository.PostRepository;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import com.vikisol.arena.rooms.repository.RoomMemberRepository;
import com.vikisol.arena.rooms.repository.RoomMessageRepository;
import com.vikisol.arena.rooms.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// B11 item 2: DataSeeder must fail startup loudly if SEED_ENABLED is somehow true outside the
// 'local' profile (e.g. a misconfigured production/staging deploy), rather than quietly seeding
// fake companies and candidates into a real database.
class DataSeederGuardTest {

    @Test
    void refusesToRunOutsideTheLocalProfileEvenWithoutTouchingTheDatabase() {
        EnterpriseProfileRepository enterpriseProfileRepository = mock(EnterpriseProfileRepository.class);
        Environment environment = mock(Environment.class);
        when(environment.matchesProfiles("local")).thenReturn(false);

        DataSeeder seeder = new DataSeeder(
                mock(UserRepository.class), mock(DemoPassword.class), mock(CandidateProfileRepository.class),
                enterpriseProfileRepository, mock(MembershipRepository.class), mock(JobPostingRepository.class),
                mock(ApplicationRepository.class), mock(InterviewRepository.class), mock(ProjectRepository.class),
                mock(BidRepository.class), mock(MilestoneRepository.class), mock(ShortlistEntryRepository.class),
                mock(UnlockedCandidateRepository.class), mock(NotificationService.class), mock(ActivityService.class),
                mock(ScoringService.class), mock(PasswordEncoder.class), mock(JdbcTemplate.class),
                mock(PostRepository.class), mock(PostJoinRequestRepository.class), mock(RoomRepository.class),
                mock(RoomMemberRepository.class), mock(RoomMessageRepository.class), mock(FollowRepository.class),
                environment, mock(jakarta.persistence.EntityManager.class));

        assertThatThrownBy(() -> seeder.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refusing to seed demo data");
        // Never even checked whether the database already has seed data - the profile guard is
        // the very first thing run() does, before any query.
        verify(enterpriseProfileRepository, never()).count();
    }

    @Test
    void doesNotThrowOnTheLocalProfile() {
        EnterpriseProfileRepository enterpriseProfileRepository = mock(EnterpriseProfileRepository.class);
        when(enterpriseProfileRepository.count()).thenReturn(1L); // already seeded - short-circuits right after the guard
        Environment environment = mock(Environment.class);
        when(environment.matchesProfiles("local")).thenReturn(true);

        DataSeeder seeder = new DataSeeder(
                mock(UserRepository.class), mock(DemoPassword.class), mock(CandidateProfileRepository.class),
                enterpriseProfileRepository, mock(MembershipRepository.class), mock(JobPostingRepository.class),
                mock(ApplicationRepository.class), mock(InterviewRepository.class), mock(ProjectRepository.class),
                mock(BidRepository.class), mock(MilestoneRepository.class), mock(ShortlistEntryRepository.class),
                mock(UnlockedCandidateRepository.class), mock(NotificationService.class), mock(ActivityService.class),
                mock(ScoringService.class), mock(PasswordEncoder.class), mock(JdbcTemplate.class),
                mock(PostRepository.class), mock(PostJoinRequestRepository.class), mock(RoomRepository.class),
                mock(RoomMemberRepository.class), mock(RoomMessageRepository.class), mock(FollowRepository.class),
                environment, mock(jakarta.persistence.EntityManager.class));

        seeder.run(mock(ApplicationArguments.class)); // must not throw
        verify(enterpriseProfileRepository).count();
    }

    // ARCHITECT-REVIEW (MARATHON-BE-2) item 4: a child entity cascade-persisted through its
    // parent (e.g. Interview.proposedSlots) doesn't get its @PrePersist hook run until Hibernate
    // actually flushes the insert - run() must flush while DemoSeedingContext is still active, not
    // after. The seeding body itself can't run against bare mocks, so this proves the ordering the
    // way it actually matters: whatever happens in between, entityManager.flush() is called while
    // the context is still active, strictly before DemoSeedingContext.end() clears it.
    @Test
    void flushesInsideDemoSeedingContextBeforeEndingItEvenWhenSeedingFailsPartway() {
        EnterpriseProfileRepository enterpriseProfileRepository = mock(EnterpriseProfileRepository.class);
        when(enterpriseProfileRepository.count()).thenReturn(0L); // not already seeded - proceeds into the body
        Environment environment = mock(Environment.class);
        when(environment.matchesProfiles("local")).thenReturn(true);
        jakarta.persistence.EntityManager entityManager = mock(jakarta.persistence.EntityManager.class);
        boolean[] activeWhenFlushed = {false};
        doAnswer(inv -> {
            activeWhenFlushed[0] = DemoSeedingContext.isActive();
            return null;
        }).when(entityManager).flush();

        DataSeeder seeder = new DataSeeder(
                mock(UserRepository.class), mock(DemoPassword.class), mock(CandidateProfileRepository.class),
                enterpriseProfileRepository, mock(MembershipRepository.class), mock(JobPostingRepository.class),
                mock(ApplicationRepository.class), mock(InterviewRepository.class), mock(ProjectRepository.class),
                mock(BidRepository.class), mock(MilestoneRepository.class), mock(ShortlistEntryRepository.class),
                mock(UnlockedCandidateRepository.class), mock(NotificationService.class), mock(ActivityService.class),
                mock(ScoringService.class), mock(PasswordEncoder.class), mock(JdbcTemplate.class),
                mock(PostRepository.class), mock(PostJoinRequestRepository.class), mock(RoomRepository.class),
                mock(RoomMemberRepository.class), mock(RoomMessageRepository.class), mock(FollowRepository.class),
                environment, entityManager);

        // The seeding body can't complete against bare mocks (no real JPA/DB behind them) - it's
        // guaranteed to throw partway through. The finally block (flush, then end) still runs.
        assertThatThrownBy(() -> seeder.run(mock(ApplicationArguments.class)));
        verify(entityManager).flush();
        assertThat(activeWhenFlushed[0]).as("DemoSeedingContext must still be active when flush() runs").isTrue();
        assertThat(DemoSeedingContext.isActive()).as("context must be cleared after run() returns/throws").isFalse();
    }
}
