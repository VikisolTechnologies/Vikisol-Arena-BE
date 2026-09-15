package com.vikisol.arena.seed;

import com.vikisol.arena.applications.entity.Application;
import com.vikisol.arena.applications.entity.ApplicationStage;
import com.vikisol.arena.applications.repository.ApplicationRepository;
import com.vikisol.arena.auth.dto.SignUpRequest;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.auth.service.AuthService;
import com.vikisol.arena.common.geo.GeohashUtil;
import com.vikisol.arena.enterprise.dto.admin.InviteMemberRequest;
import com.vikisol.arena.enterprise.entity.CompanySize;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.entity.Membership;
import com.vikisol.arena.enterprise.entity.MembershipStatus;
import com.vikisol.arena.enterprise.entity.Plan;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import com.vikisol.arena.enterprise.repository.InvitationRepository;
import com.vikisol.arena.enterprise.repository.MembershipRepository;
import com.vikisol.arena.enterprise.repository.ShortlistEntryRepository;
import com.vikisol.arena.enterprise.repository.UnlockedCandidateRepository;
import com.vikisol.arena.enterprise.service.TeamService;
import com.vikisol.arena.follows.entity.Follow;
import com.vikisol.arena.follows.repository.FollowRepository;
import com.vikisol.arena.interviews.entity.Interview;
import com.vikisol.arena.interviews.entity.InterviewSlot;
import com.vikisol.arena.interviews.entity.InterviewStatus;
import com.vikisol.arena.interviews.repository.InterviewRepository;
import com.vikisol.arena.jobs.entity.EmploymentType;
import com.vikisol.arena.jobs.entity.JobPosting;
import com.vikisol.arena.jobs.entity.PostingStatus;
import com.vikisol.arena.jobs.repository.JobPostingRepository;
import com.vikisol.arena.marketplace.entity.Bid;
import com.vikisol.arena.marketplace.entity.BidStatus;
import com.vikisol.arena.marketplace.entity.Milestone;
import com.vikisol.arena.marketplace.entity.MilestoneStatus;
import com.vikisol.arena.marketplace.entity.Project;
import com.vikisol.arena.marketplace.entity.ProjectStatus;
import com.vikisol.arena.marketplace.repository.BidRepository;
import com.vikisol.arena.marketplace.repository.MilestoneRepository;
import com.vikisol.arena.marketplace.repository.ProjectRepository;
import com.vikisol.arena.messaging.service.ConversationService;
import com.vikisol.arena.notifications.entity.NotificationType;
import com.vikisol.arena.notifications.repository.NotificationRepository;
import com.vikisol.arena.notifications.service.NotificationService;
import com.vikisol.arena.platform.repository.ModerationItemRepository;
import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.entity.PostAudience;
import com.vikisol.arena.posts.entity.PostComment;
import com.vikisol.arena.posts.entity.PostIntentType;
import com.vikisol.arena.posts.entity.PostJoinRequest;
import com.vikisol.arena.posts.entity.PostJoinStatus;
import com.vikisol.arena.posts.entity.PostReaction;
import com.vikisol.arena.posts.entity.PostSave;
import com.vikisol.arena.posts.entity.PostStatus;
import com.vikisol.arena.posts.entity.PostVisibility;
import com.vikisol.arena.posts.repository.PostCommentRepository;
import com.vikisol.arena.posts.repository.PostJoinRequestRepository;
import com.vikisol.arena.posts.repository.PostReactionRepository;
import com.vikisol.arena.posts.repository.PostRepository;
import com.vikisol.arena.posts.repository.PostSaveRepository;
import com.vikisol.arena.profile.entity.AutonomyLevel;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.entity.CandidateSkill;
import com.vikisol.arena.profile.entity.ConsentSettings;
import com.vikisol.arena.profile.entity.Industry;
import com.vikisol.arena.profile.entity.LocationConsent;
import com.vikisol.arena.profile.entity.OpenTo;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import com.vikisol.arena.rooms.entity.Room;
import com.vikisol.arena.rooms.entity.RoomMember;
import com.vikisol.arena.rooms.entity.RoomMemberRole;
import com.vikisol.arena.rooms.entity.RoomMessage;
import com.vikisol.arena.rooms.repository.RoomMemberRepository;
import com.vikisol.arena.rooms.repository.RoomMessageRepository;
import com.vikisol.arena.rooms.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * ARENA-WEB-AND-SEED.md Part 4 + ARENA-FINISH-IT.md §1. An on-demand, labeled, fully-removable
 * content overlay - distinct from the original DataSeeder bootstrap (runs once on first boot, no
 * demoContent marker, stale-dated posts). Every entity this service creates gets
 * BaseEntity.demoContent = true. seed()/removeAll() are the "one documented command" each way -
 * see SEED-CONTENT.md and DemoContentController's own comment for why the whole controller bean
 * is absent, not just inert, when app.demo-content.enabled (ARENA_SEED_MODE) is off.
 *
 * Accounts go through the REAL service layer, not direct repository saves: authService.signUp()
 * for the two self-serve roles (talent, company_admin - the same validation, password hashing,
 * and starter-profile creation a real signup gets), teamService.invite() +
 * teamService.acceptInvitation() for recruiter/hiring_manager (this product's only real path for
 * those roles - see AuthService.signUp()'s own "ask your admin for an invite" refusal for those
 * roles). platform_admin has no self-service path at all, by design, in this product - the demo
 * account for that role reuses the same direct-creation pattern the original DataSeeder already
 * uses for the one platform_admin account that exists today, not a fabricated "real path" that
 * doesn't exist.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DemoContentService {

    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final PostRepository postRepository;
    private final PostJoinRequestRepository postJoinRequestRepository;
    private final PostCommentRepository postCommentRepository;
    private final PostReactionRepository postReactionRepository;
    private final PostSaveRepository postSaveRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomMessageRepository roomMessageRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final EnterpriseProfileRepository enterpriseProfileRepository;
    private final MembershipRepository membershipRepository;
    private final InvitationRepository invitationRepository;
    private final JobPostingRepository jobPostingRepository;
    private final ProjectRepository projectRepository;
    private final BidRepository bidRepository;
    private final MilestoneRepository milestoneRepository;
    private final ApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;
    private final ModerationItemRepository moderationItemRepository;
    private final FollowRepository followRepository;
    private final ShortlistEntryRepository shortlistEntryRepository;
    private final UnlockedCandidateRepository unlockedCandidateRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final TeamService teamService;
    private final ConversationService conversationService;

    // ARENA-FINISH-IT.md §1.1 - "one shared, simple, documented password... these are throwaway
    // demo logins, not secrets." Satisfies both SignUpRequest's 6-char and
    // AcceptInvitationRequest's 8-char minimums.
    public static final String DEMO_PASSWORD = "ArenaDemo2026!";
    private static final String EMAIL_DOMAIN = "demo.arena.test";

    private record Neighborhood(String name, double lat, double lng) {}
    private static final List<Neighborhood> NEIGHBORHOODS = List.of(
            new Neighborhood("Gachibowli", 17.4400, 78.3489),
            new Neighborhood("Gopanapally", 17.4602, 78.3106),
            new Neighborhood("Madhapur", 17.4483, 78.3915),
            new Neighborhood("Kondapur", 17.4615, 78.3672),
            new Neighborhood("Hitec City", 17.4435, 78.3772)
    );

    public record SeedSummary(int accounts, int companies, int posts, int comments, int applications,
                               int projects, int bids, int rooms, int conversations, int notifications) {}
    public record RemovalSummary(int accounts, int companies, int posts, int jobPostings, int projects, int rooms) {}

    @Transactional
    public SeedSummary seed() {
        if (!postRepository.findByDemoContentTrue().isEmpty()) {
            log.info("Demo content already present - skipping seed (call removeAll() first to reseed)");
            return new SeedSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        // TEMP diagnostic wrapping (2026-09-15) - a first live attempt got 10s into this call
        // (past seedTalentAccounts, given BCrypt alone takes that long for 40 accounts) before
        // failing with another message-less RuntimeException; RuntimeException.class's handler
        // in GlobalExceptionHandler only logs ex.getMessage(), not a stack trace, so there's no
        // way to tell which step or line from Railway logs alone. Naming each step so the next
        // failure's full stack trace (via log.error, which DOES print one) points at the exact
        // spot - remove once seed() runs clean end to end.
        List<CandidateProfile> talent = diag("seedTalentAccounts", this::seedTalentAccounts);
        List<EnterpriseProfile> companies = diag("seedCompanies", this::seedCompanies);
        List<User> team = diag("seedEnterpriseTeam", () -> seedEnterpriseTeam(companies.get(0)));
        diag("seedPlatformAdminAccount", () -> { seedPlatformAdminAccount(); return null; });

        List<Post> posts = diag("seedPosts", () -> seedPosts(talent));
        int commentCount = diag("seedCommentsReactionsSaves", () -> seedCommentsReactionsSaves(posts, talent));
        int roomCount = diag("seedRoomsAndMessages", () -> seedRoomsAndMessages(posts, talent));
        int conversationCount = diag("seedDirectAndBidConversations", () -> seedDirectAndBidConversations(talent, companies.get(0), team));

        List<JobPosting> postings = diag("seedJobPostings", () -> seedJobPostings(companies));
        int applicationCount = diag("seedApplicationsAndInterviews", () -> seedApplicationsAndInterviews(talent, postings, team));
        ProjectSeedResult projectResult = diag("seedProjectsAndBids", () -> seedProjectsAndBids(companies, talent));

        diag("seedNotifications", () -> { seedNotifications(talent, companies.get(0)); return null; });
        diag("seedFollows", () -> { seedFollows(talent); return null; });

        // 40 talent + 3 real company_admin signups + 6 invited team members + 1 platform admin =
        // 50 - companies.size() (5) isn't accounts, only the first 3 (real logins) are; the
        // other 2 are content-only backing tenants (see seedCompanies()'s own comment).
        int accountCount = talent.size() + 3 + team.size() + 1;

        log.info("Demo content seeded: {} accounts, {} companies, {} posts, {} comments, {} applications, "
                        + "{} projects, {} bids, {} rooms, {} conversations, {} notifications",
                accountCount, companies.size(), posts.size(), commentCount, applicationCount,
                projectResult.projects().size(), projectResult.bidCount(), roomCount, conversationCount, 4);
        return new SeedSummary(accountCount, companies.size(), posts.size(), commentCount, applicationCount,
                projectResult.projects().size(), projectResult.bidCount(), roomCount, conversationCount, 4);
    }

    // TEMP diagnostic helper - see seed()'s own comment. log.error(..., e) DOES print a full
    // stack trace to Railway logs, unlike GlobalExceptionHandler's generic RuntimeException.class
    // handler (message only) that would otherwise be all that's visible for an unnamed failure.
    private <T> T diag(String step, java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (RuntimeException e) {
            log.error("DIAG seed() step [{}] failed", step, e);
            throw e;
        }
    }

    // ---------------------------------------------------------------------------------------
    // §1.1 Accounts
    // ---------------------------------------------------------------------------------------

    // 40 talent accounts (user01-user40), through the real signup endpoint's own service method.
    // One (index 39, user40) is deliberately sparse - §1.1/§4.4's "thin state" test case: no
    // skills, no bio, minimal experience, exactly what a real just-signed-up account looks like
    // before onboarding, left that way rather than enriched like the other 39.
    private List<CandidateProfile> seedTalentAccounts() {
        List<CandidateProfile> candidates = new ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            String name = IndianData.fullName();
            String email = String.format("user%02d@%s", i, EMAIL_DOMAIN);
            authService.signUp(new SignUpRequest(name, email, DEMO_PASSWORD, "talent"), false);
            User user = withDemoFlag(userRepository.findByEmailIgnoreCase(email).orElseThrow());
            userRepository.save(user);

            CandidateProfile profile = candidateProfileRepository.findByUserId(user.getId()).orElseThrow();
            boolean sparse = i == 40;
            if (!sparse) {
                Industry industry = IndianData.pick(IndianData.INDUSTRIES_LIST());
                int experienceYears = IndianData.intBetween(0, 12);
                Neighborhood home = IndianData.pick(NEIGHBORHOODS);
                // A real, live bug found and fixed here (2026-09-15): this used to end in
                // .toList() (Java's Stream method, which explicitly returns an IMMUTABLE list),
                // then get assigned via profile.setSkills(skills) onto an ALREADY-PERSISTED
                // entity fetched from the repository above - not set at construction time on a
                // brand-new one. Hibernate tries to mutate the collection you hand it when
                // replacing an @ElementCollection field on a managed entity; an immutable list
                // throws UnsupportedOperationException with no message at that point. Wrapping
                // in a real, mutable ArrayList fixes it - see IndianData.pickN()'s own comment,
                // fixed at the source too since every other pickN() call in this file has the
                // same exposure.
                List<CandidateSkill> skills = new ArrayList<>(IndianData.pickN(IndianData.SKILLS_BY_INDUSTRY.get(industry), IndianData.intBetween(3, 6)).stream()
                        .map(s -> new CandidateSkill(s, IndianData.RANDOM.nextDouble() < 0.4))
                        .toList());
                double jitterLat = home.lat() + (IndianData.RANDOM.nextDouble() - 0.5) * 0.01;
                double jitterLng = home.lng() + (IndianData.RANDOM.nextDouble() - 0.5) * 0.01;
                String geohash = GeohashUtil.encode(jitterLat, jitterLng);
                double[] approx = GeohashUtil.decode(geohash);

                profile.setTitle(IndianData.pick(IndianData.TITLES_BY_INDUSTRY.get(industry)));
                profile.setIndustry(industry);
                profile.setLocation(home.name() + ", Hyderabad");
                profile.setRemote(IndianData.RANDOM.nextDouble() < 0.3);
                profile.setSkills(skills);
                profile.setExperienceYears(experienceYears);
                profile.setRateFloor(IndianData.intBetween(6, 35));
                profile.setOpenTo(IndianData.pickN(List.of(OpenTo.FULL_TIME, OpenTo.CONTRACT, OpenTo.PROJECTS), IndianData.intBetween(1, 2)));
                profile.setConsent(new ConsentSettings(IndianData.RANDOM.nextDouble() < 0.6, true));
                profile.setAutonomy(IndianData.pick(List.of(AutonomyLevel.MANUAL, AutonomyLevel.SUPERVISED, AutonomyLevel.AUTOPILOT)));
                profile.setBio(experienceYears + "+ years in " + industry.wireValue().toLowerCase() + ", " + home.name() + ".");
                // First 25 get real precise-consent coordinates, spread across all 5
                // neighborhoods, so Home/Map's nearby query has genuine density everywhere.
                if (i <= 25) {
                    profile.setLocationConsent(LocationConsent.PRECISE);
                    profile.setGeohash(geohash);
                    profile.setApproxLat(approx[0]);
                    profile.setApproxLng(approx[1]);
                }
            }
            profile.setDemoContent(true);
            candidates.add(candidateProfileRepository.save(profile));
        }
        return candidates;
    }

    // 3 real company_admin signups (each starts its own tenant, per AuthService.signUp()), then
    // enriched from a blank starter company into a named one - plus 2 lightweight extra tenants
    // (no dedicated login) purely so §1.2's "5 demo companies" content target is met without
    // fabricating logins nobody asked for. user41-43 are the 3 admins.
    private List<EnterpriseProfile> seedCompanies() {
        record CompanySeed(String name, String emoji, Industry industry) {}
        List<CompanySeed> realSeeds = List.of(
                new CompanySeed("Preview Labs", "🔶", Industry.ENGINEERING),
                new CompanySeed("Northstar Design Co", "🎨", Industry.DESIGN),
                new CompanySeed("Meridian Health Partners", "🩺", Industry.HEALTHCARE)
        );
        List<EnterpriseProfile> companies = new ArrayList<>();
        for (int i = 0; i < realSeeds.size(); i++) {
            CompanySeed seed = realSeeds.get(i);
            String email = String.format("user%02d@%s", 41 + i, EMAIL_DOMAIN);
            String adminName = seed.name() + " Talent Team";
            authService.signUp(new SignUpRequest(adminName, email, DEMO_PASSWORD, "company_admin"), false);
            User admin = userRepository.findByEmailIgnoreCase(email).orElseThrow();
            admin.setDemoContent(true);
            userRepository.save(admin);

            EnterpriseProfile profile = enterpriseProfileRepository.findByUserId(admin.getId()).orElseThrow();
            profile.setCompanyName(seed.name());
            profile.setLogoEmoji(seed.emoji());
            profile.setIndustry(seed.industry());
            profile.setSize(CompanySize.S_11_50);
            // ArrayList, not List.of() - see seedTalentAccounts()'s comment on why an immutable
            // list here throws when Hibernate tries to mutate this @ElementCollection field on
            // an already-persisted entity.
            profile.setHiringFor(new ArrayList<>(List.of("Engineers", "Designers", "Sales reps")));
            profile.setPlan(Plan.PRO);
            // Room for the 6 invited teammates (§1.1: 5 recruiter + 1 hiring_manager) plus the
            // admin itself - seatsTotal gates TeamService.invite()'s own seat-limit check.
            profile.setSeatsTotal(10);
            profile.setUnlockCreditsTotal(25);
            profile.setDemoContent(true);
            companies.add(enterpriseProfileRepository.save(profile));

            Membership membership = membershipRepository.findByUserId(admin.getId()).orElseThrow();
            membership.setDemoContent(true);
            membershipRepository.save(membership);
        }

        // 2 more, content-only (no dedicated admin login) - IndianData.COMPANIES gives real
        // Indian-market company names/emojis distinct from the 3 above.
        for (int i = 0; i < 2; i++) {
            IndianData.CompanySeed seed = IndianData.COMPANIES.get(i);
            User user = withDemoFlag(User.builder()
                    .email(String.format("company-backing-%d.%s@%s", i, System.nanoTime(), EMAIL_DOMAIN))
                    .passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                    .name(seed.name() + " Talent Team")
                    .role(Role.COMPANY_ADMIN)
                    .handle(com.vikisol.arena.common.util.HandleGenerator.generate(seed.name() + " Talent Team", userRepository::existsByHandle))
                    .build());
            user = userRepository.save(user);
            EnterpriseProfile profile = EnterpriseProfile.builder()
                    .user(user).companyName(seed.name()).logoEmoji(seed.emoji())
                    .industry(IndianData.pick(IndianData.INDUSTRIES_LIST()))
                    .size(CompanySize.S_51_200).hiringFor(List.of("Engineers", "Support staff"))
                    .plan(Plan.FREE).seatsUsed(1).seatsTotal(3).unlockCreditsUsed(0).unlockCreditsTotal(10)
                    .build();
            profile.setDemoContent(true);
            profile = enterpriseProfileRepository.save(profile);
            Membership membership = Membership.builder().user(user).tenant(profile).status(MembershipStatus.ACTIVE).joinedAt(user.getCreatedAt()).build();
            membership.setDemoContent(true);
            membershipRepository.save(membership);
            companies.add(profile);
        }
        return companies;
    }

    // §1.1's remaining roles: 5 recruiter + 1 hiring_manager (user44-49), through the REAL
    // invite -> accept flow against the first real company - the only path this product has for
    // these two roles (AuthService.signUp() refuses them outright).
    private List<User> seedEnterpriseTeam(EnterpriseProfile primaryCompany) {
        UUID adminId = primaryCompany.getUser().getId();
        List<User> team = new ArrayList<>();
        record Invitee(String name, Role role) {}
        List<Invitee> invitees = List.of(
                new Invitee("Priyanka Rao", Role.RECRUITER),
                new Invitee("Karthik Iyer", Role.RECRUITER),
                new Invitee("Divya Menon", Role.RECRUITER),
                new Invitee("Rahul Kapoor", Role.RECRUITER),
                new Invitee("Sneha Pillai", Role.RECRUITER),
                new Invitee("Arjun Nair", Role.HIRING_MANAGER)
        );
        for (int i = 0; i < invitees.size(); i++) {
            Invitee invitee = invitees.get(i);
            String email = String.format("user%02d@%s", 44 + i, EMAIL_DOMAIN);
            teamService.invite(adminId, new InviteMemberRequest(email, invitee.role().wireValue()));
            var invitation = invitationRepository.findByTenantIdAndEmailIgnoreCase(primaryCompany.getId(), email).orElseThrow();
            invitation.setDemoContent(true);
            invitationRepository.save(invitation);

            User user = teamService.acceptInvitation(invitation.getToken(), invitee.name(), DEMO_PASSWORD);
            user.setDemoContent(true);
            userRepository.save(user);
            Membership membership = membershipRepository.findByUserId(user.getId()).orElseThrow();
            membership.setDemoContent(true);
            membershipRepository.save(membership);
            team.add(user);
        }
        return team;
    }

    // §1.1 platform_admin - no self-service path exists for this role anywhere in this product
    // (by design, for security - see the class-level comment). The original DataSeeder already
    // created exactly one (admin@vikisol.dev) this same direct way; user50 mirrors it rather
    // than pretending a "real path" exists where none does.
    private User seedPlatformAdminAccount() {
        String email = String.format("user50@%s", EMAIL_DOMAIN);
        User user = withDemoFlag(User.builder()
                .email(email).passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                .name("Demo Platform Admin").role(Role.PLATFORM_ADMIN)
                .handle(com.vikisol.arena.common.util.HandleGenerator.generate("Demo Platform Admin", userRepository::existsByHandle))
                .build());
        return userRepository.save(user);
    }

    private User withDemoFlag(User user) {
        user.setDemoContent(true);
        return user;
    }

    // ---------------------------------------------------------------------------------------
    // §1.2 Content
    // ---------------------------------------------------------------------------------------

    private record ActivitySeed(String body, Neighborhood where, PostVisibility visibility, Integer capacity, int startsInHours, String meetingPoint) {}
    private record NeedSeed(String body) {}

    private List<Post> seedPosts(List<CandidateProfile> candidates) {
        List<ActivitySeed> activityTemplates = List.of(
                new ActivitySeed("Badminton doubles tonight, need 2 more - court's already booked", NEIGHBORHOODS.get(0), PostVisibility.PUBLIC, 4, 3, "Smash Badminton Academy, Gachibowli - Court 2"),
                new ActivitySeed("Morning cricket - 6-a-side, casual, all skill levels welcome", NEIGHBORHOODS.get(3), PostVisibility.PUBLIC, 12, 14, "Kondapur Community Ground, near the water tank"),
                new ActivitySeed("UI/UX design jam - bring a half-finished project, leave with feedback", NEIGHBORHOODS.get(1), PostVisibility.APPROVAL, 8, 5, "WeWork Gopanapally, 3rd floor breakout room"),
                new ActivitySeed("Weekend cycling meet - Durgam Cheruvu loop, easy pace, first-timers fine", NEIGHBORHOODS.get(0), PostVisibility.PUBLIC, 10, 40, "Durgam Cheruvu main gate, west entrance"),
                new ActivitySeed("React + TypeScript study group - working through a real codebase together", NEIGHBORHOODS.get(2), PostVisibility.PUBLIC, 6, 8, "Madhapur Public Library, 2nd floor study room"),
                new ActivitySeed("Startup weekend hackathon kickoff - form teams, pitch by Sunday", NEIGHBORHOODS.get(0), PostVisibility.APPROVAL, 30, 20, "T-Hub, Gachibowli - main auditorium"),
                new ActivitySeed("Sci-fi book club - this month's pick is a Ted Chiang collection", NEIGHBORHOODS.get(3), PostVisibility.PUBLIC, 8, 6, "Roastery Coffee House, Kondapur"),
                new ActivitySeed("Football, 5-a-side, turf's booked till 9", NEIGHBORHOODS.get(1), PostVisibility.PUBLIC, 10, 4, "Play Arena Turf, Gopanapally"),
                new ActivitySeed("Sunrise photography walk - HITEC City to Madhapur, bring any camera", NEIGHBORHOODS.get(4), PostVisibility.PUBLIC, 6, 60, "Cyber Towers main gate, HITEC City"),
                new ActivitySeed("Chess meetup - bring a board if you have one, a few spares available", NEIGHBORHOODS.get(0), PostVisibility.APPROVAL, 8, 30, "Gachibowli Community Hall, room 4"),
                new ActivitySeed("Sunrise yoga - all levels, mats available to borrow", NEIGHBORHOODS.get(3), PostVisibility.PUBLIC, 15, 16, "Kondapur District Park, near the jogging track"),
                new ActivitySeed("Board games evening - Catan, Codenames, whatever people bring", NEIGHBORHOODS.get(1), PostVisibility.PUBLIC, 6, 7, "Community clubhouse, Gopanapally Phase 2"),
                new ActivitySeed("Weekend trek - Ananthagiri Hills, early start, first-timers welcome", NEIGHBORHOODS.get(3), PostVisibility.APPROVAL, 6, 50, "Vikarabad bus stand, 5:30am sharp"),
                new ActivitySeed("Volleyball, beach-style on sand court", NEIGHBORHOODS.get(4), PostVisibility.PUBLIC, 12, 26, "Hitec City Sports Complex, court 1"),
                new ActivitySeed("Pottery workshop - beginner friendly, materials provided", NEIGHBORHOODS.get(2), PostVisibility.APPROVAL, 8, 34, "ClayWorks Studio, Madhapur"),
                new ActivitySeed("Running club - 5K easy pace, then coffee", NEIGHBORHOODS.get(0), PostVisibility.PUBLIC, 20, 15, "Gachibowli Stadium outer track, gate 2"),
                new ActivitySeed("Live music jam - bring an instrument or just listen", NEIGHBORHOODS.get(1), PostVisibility.PUBLIC, 15, 28, "Rooftop, Gopanapally Phase 1"),
                new ActivitySeed("Table tennis league night - singles bracket, all levels", NEIGHBORHOODS.get(3), PostVisibility.PUBLIC, 8, 12, "Kondapur Sports Club"),
                new ActivitySeed("Career-switchers meetup - swap notes on moving into tech", NEIGHBORHOODS.get(2), PostVisibility.APPROVAL, 12, 45, "Madhapur Co-working Hub, event room"),
                new ActivitySeed("Weekend farmers market volunteer morning", NEIGHBORHOODS.get(4), PostVisibility.PUBLIC, 10, 55, "Hitec City Community Ground"),
                // §1.2/§4.4 edge case - zero joins, nobody's said yes yet.
                new ActivitySeed("Late-night coding jam - bring a side project, snacks provided", NEIGHBORHOODS.get(0), PostVisibility.APPROVAL, 10, 70, "Gachibowli maker space, back room"),
                new ActivitySeed("Salsa dancing - absolute beginners welcome, no partner needed", NEIGHBORHOODS.get(1), PostVisibility.PUBLIC, 16, 22, "Dance Studio Gopanapally"),
                new ActivitySeed("Rock climbing gym session - top-rope, gear rental on site", NEIGHBORHOODS.get(3), PostVisibility.PUBLIC, 6, 18, "Boulder Box Kondapur"),
                new ActivitySeed("Investing 101 - a casual, no-pitch discussion over coffee", NEIGHBORHOODS.get(2), PostVisibility.APPROVAL, 10, 65, "Third Wave Coffee, Madhapur")
        );
        List<NeedSeed> needTemplates = List.of(
                new NeedSeed("Need someone experienced in both React Native and native iOS to help debug a really "
                        + "specific animation performance issue that only shows up on older Android devices under "
                        + "memory pressure, happy to pay for a couple of hours of pairing this week if anyone's free"),
                new NeedSeed("Anyone have a spare badminton racket for tonight's game near Gachibowli?"),
                new NeedSeed("Good GST-compliant invoicing tool for freelancers? Tired of doing this by hand"),
                new NeedSeed("Need 2 more flatmates near Kondapur - IT professionals preferred, move-in this month"),
                new NeedSeed("Looking for a daily carpool partner, Gachibowli to Madhapur commute"),
                new NeedSeed("Searching for a Spring Boot mentor - a couple of hours a week, can pay"),
                new NeedSeed("Anyone selling a used standing desk in Hyderabad? Preferably Gachibowli area"),
                new NeedSeed("Looking for a Figma expert to review a portfolio - 30 minutes, happy to pay"),
                new NeedSeed("Need a second opinion on a freelance contract before I sign it"),
                new NeedSeed("Anyone know a reliable AC repair person near Kondapur?"),
                new NeedSeed("Looking for a co-founder with a sales background, early-stage SaaS idea"),
                new NeedSeed("Need someone to proofread a grant application, due Friday"),
                new NeedSeed("Anyone have a referral at Zoho or Freshworks? Applying this week"),
                new NeedSeed("Looking for a Hindi-English translator for a short video, one-off gig"),
                new NeedSeed("Need help moving a couch this Saturday, Gachibowli to Madhapur"),
                new NeedSeed("Anyone rent out a DSLR for a weekend shoot?"),
                new NeedSeed("Looking for a running partner, early mornings, Gachibowli area"),
                new NeedSeed("Need advice on switching from a service company to a product company"),
                new NeedSeed("Anyone selling a used bicycle, road or hybrid, Hyderabad"),
                new NeedSeed("Looking for a part-time bookkeeper for a small business, few hours a week"),
                new NeedSeed("Need someone who's done a UK visa application recently - a few questions"),
                new NeedSeed("Anyone have a spare desk chair? Working from home now"),
                new NeedSeed("Looking for a design mentor for a portfolio review before interviews start"),
                new NeedSeed("Need a plumber recommendation near Kondapur, nothing urgent"),
                new NeedSeed("Anyone up for a language exchange - Telugu for Spanish?")
        );

        List<Post> saved = new ArrayList<>();
        for (int i = 0; i < activityTemplates.size(); i++) {
            ActivitySeed seed = activityTemplates.get(i);
            CandidateProfile author = candidates.get(i % candidates.size());
            String geohash = GeohashUtil.encode(seed.where().lat(), seed.where().lng());
            double[] approx = GeohashUtil.decode(geohash);
            Integer capacity = seed.capacity();
            // Vary joins from 0 to full across the set (§4.4 edge case: index 20's activity
            // above is one of the ones that lands at zero).
            int spotsFilled = capacity == null ? 0 : (i == 20 ? 0 : IndianData.intBetween(0, capacity));
            Post post = Post.builder()
                    .authorUser(author.getUser())
                    .intentType(PostIntentType.ACTIVITY)
                    .body(seed.body())
                    .locationText(seed.where().name())
                    .audience(PostAudience.GLOBAL)
                    .visibility(seed.visibility())
                    .capacity(capacity)
                    .spotsFilled(spotsFilled)
                    .status(capacity != null && spotsFilled >= capacity ? PostStatus.FULL : PostStatus.OPEN)
                    .startsAt(Instant.now().plus(Duration.ofHours(seed.startsInHours())))
                    .exactMeetingPoint(seed.meetingPoint())
                    .geohash(geohash).approxLat(approx[0]).approxLng(approx[1])
                    .build();
            post.setDemoContent(true);
            saved.add(postRepository.save(post));
        }
        for (int i = 0; i < needTemplates.size(); i++) {
            CandidateProfile author = candidates.get((i + 7) % candidates.size());
            // §4.4 edge case: the first need (index 0) carries a deliberately very long body.
            Post post = Post.builder()
                    .authorUser(author.getUser())
                    .intentType(PostIntentType.ASK)
                    .body(needTemplates.get(i).body())
                    .audience(PostAudience.GLOBAL)
                    .visibility(PostVisibility.PUBLIC)
                    .status(PostStatus.OPEN)
                    .build();
            post.setDemoContent(true);
            saved.add(postRepository.save(post));
        }
        return saved;
    }

    // ~60+ comments spread unevenly, reactions + saves at uneven density - some posts busy, some
    // with one comment, most with none, matching §1.2's own "not more than needed" instruction.
    private int seedCommentsReactionsSaves(List<Post> posts, List<CandidateProfile> candidates) {
        List<String> commentTemplates = List.of(
                "Count me in!", "What time exactly?", "Is this still happening?", "Can I bring a friend?",
                "Perfect, see you there.", "Following - interested if a spot opens up.",
                "Been wanting to try this, thanks for organizing.", "Any prep needed beforehand?",
                "Same, this is exactly what I was looking for.", "How do I get there by public transport?",
                "Second this - great idea.", "Is there a WhatsApp group for coordination?"
        );
        int commentCount = 0;
        for (int i = 0; i < posts.size(); i++) {
            Post post = posts.get(i);
            // Skewed distribution: ~15% of posts get 4-6 comments, ~35% get 1-2, the rest get none.
            int n = i % 7 == 0 ? IndianData.intBetween(4, 6) : i % 3 == 0 ? IndianData.intBetween(1, 2) : 0;
            for (int c = 0; c < n; c++) {
                CandidateProfile commenter = candidates.get((i * 3 + c + 1) % candidates.size());
                PostComment comment = PostComment.builder()
                        .post(post).authorUser(commenter.getUser())
                        .content(IndianData.pick(commentTemplates))
                        .build();
                comment.setDemoContent(true);
                postCommentRepository.save(comment);
                commentCount++;
            }
            // Reactions/saves at uneven, unrelated density.
            int reactors = IndianData.intBetween(0, Math.min(6, candidates.size()));
            for (CandidateProfile reactor : IndianData.pickN(candidates, reactors)) {
                if (reactor.getUser().getId().equals(post.getAuthorUser().getId())) continue;
                PostReaction reaction = PostReaction.builder().post(post).user(reactor.getUser()).build();
                reaction.setDemoContent(true);
                postReactionRepository.save(reaction);
            }
            if (IndianData.RANDOM.nextDouble() < 0.25) {
                CandidateProfile saver = IndianData.pick(candidates);
                if (!saver.getUser().getId().equals(post.getAuthorUser().getId())) {
                    PostSave save = PostSave.builder().post(post).user(saver.getUser()).build();
                    save.setDemoContent(true);
                    postSaveRepository.save(save);
                }
            }
        }
        return commentCount;
    }

    // Group activity rooms with pinned meeting points - approve one join per selected activity
    // post, create the Room, seed a short (or, for one, §4.4's deliberately very long) message
    // thread.
    private int seedRoomsAndMessages(List<Post> posts, List<CandidateProfile> candidates) {
        int[] roomPostIndexes = {0, 1, 4, 9}; // badminton, cricket, study group, chess
        int roomCount = 0;
        for (int idx = 0; idx < roomPostIndexes.length; idx++) {
            Post post = posts.get(roomPostIndexes[idx]);
            CandidateProfile joiner = candidates.get((roomPostIndexes[idx] + 5) % candidates.size());
            if (joiner.getUser().getId().equals(post.getAuthorUser().getId())) {
                joiner = candidates.get((roomPostIndexes[idx] + 6) % candidates.size());
            }

            PostJoinRequest join = PostJoinRequest.builder().post(post).user(joiner.getUser())
                    .status(PostJoinStatus.APPROVED).decidedAt(Instant.now()).build();
            join.setDemoContent(true);
            postJoinRequestRepository.save(join);

            Room room = Room.builder().post(post).build();
            room.setDemoContent(true);
            room = roomRepository.save(room);

            RoomMember admin = RoomMember.builder().room(room).user(post.getAuthorUser()).role(RoomMemberRole.ADMIN).lastReadAt(Instant.now()).build();
            admin.setDemoContent(true);
            roomMemberRepository.save(admin);
            RoomMember member = RoomMember.builder().room(room).user(joiner.getUser()).role(RoomMemberRole.MEMBER).build();
            member.setDemoContent(true);
            roomMemberRepository.save(member);

            List<String> messages = idx == 2
                    // §4.4 edge case: a deliberately very long last message.
                    ? List.of("Is this still happening today?", "Yes! Room 2nd floor, we've got the whole session booked.",
                        "One more thing before we start - if anyone hasn't already, it'd help a lot if you could skim "
                        + "through the three chapters on hooks and context we talked about last week, since today's "
                        + "session is going to build directly on that and we'd rather spend the time actually working "
                        + "through the tricky parts in the real codebase together instead of re-explaining the basics "
                        + "from scratch for whoever hasn't had a chance yet")
                    : List.of("Count me in - what time should we get there?", "Sounds good, see you there!", "Perfect.");
            User[] senders = { joiner.getUser(), post.getAuthorUser(), joiner.getUser() };
            for (int i = 0; i < messages.size(); i++) {
                RoomMessage msg = RoomMessage.builder().room(room).sender(senders[i % senders.length]).content(messages.get(i)).build();
                msg.setDemoContent(true);
                roomMessageRepository.save(msg);
            }
            roomCount++;
        }
        return roomCount;
    }

    // §1.2 "direct messages, and bid threads" - Conversation/ThreadMessage (messaging package),
    // a genuinely separate mechanism from Room/RoomMessage (which is 1:1 with a Post). Real
    // service calls (ConversationService.getOrCreate/sendMessage), same as everything else here.
    private int seedDirectAndBidConversations(List<CandidateProfile> candidates, EnterpriseProfile company, List<User> team) {
        int count = 0;
        // 2 plain direct messages between candidates.
        for (int i = 0; i < 2; i++) {
            User a = candidates.get(i).getUser();
            User b = candidates.get(i + 10).getUser();
            var conversation = conversationService.getOrCreate(a.getId(), b.getId(), null);
            conversationService.sendMessage(a.getId(), UUID.fromString(conversation.id()), "Hey, saw your post - still looking for someone?");
            conversationService.sendMessage(b.getId(), UUID.fromString(conversation.id()), "Yep! Are you interested?");
            count++;
        }
        // 2 "bid thread" style conversations - a candidate messaging the company directly about
        // a role, context field naming what it's about.
        User recruiter = team.stream().filter(u -> u.getRole() == Role.RECRUITER).findFirst().orElse(company.getUser());
        for (int i = 0; i < 2; i++) {
            User candidate = candidates.get(i + 20).getUser();
            var conversation = conversationService.getOrCreate(candidate.getId(), recruiter.getId(), "Re: Frontend Developer role");
            conversationService.sendMessage(candidate.getId(), UUID.fromString(conversation.id()), "Hi, I saw the Frontend Developer opening - is it still open?");
            conversationService.sendMessage(recruiter.getId(), UUID.fromString(conversation.id()), "Yes! Feel free to apply through the postings page.");
            count++;
        }
        return count;
    }

    // §1.2's 20 job postings, spread across the 5 companies.
    private List<JobPosting> seedJobPostings(List<EnterpriseProfile> companies) {
        record JobSeed(String title, Industry industry, EmploymentType type) {}
        List<JobSeed> titles = List.of(
                new JobSeed("Frontend Developer", Industry.ENGINEERING, EmploymentType.FULL_TIME),
                new JobSeed("Backend Developer", Industry.ENGINEERING, EmploymentType.FULL_TIME),
                new JobSeed("DevOps Engineer", Industry.ENGINEERING, EmploymentType.CONTRACT),
                new JobSeed("Product Designer", Industry.DESIGN, EmploymentType.FULL_TIME),
                new JobSeed("UI/UX Designer", Industry.DESIGN, EmploymentType.CONTRACT),
                new JobSeed("Business Development Manager", Industry.SALES, EmploymentType.FULL_TIME),
                new JobSeed("Account Executive", Industry.SALES, EmploymentType.FULL_TIME),
                new JobSeed("Registered Nurse", Industry.HEALTHCARE, EmploymentType.FULL_TIME),
                new JobSeed("Clinical Coordinator", Industry.HEALTHCARE, EmploymentType.CONTRACT),
                new JobSeed("Logistics Coordinator", Industry.LOGISTICS, EmploymentType.FULL_TIME),
                new JobSeed("Supply Chain Analyst", Industry.LOGISTICS, EmploymentType.FULL_TIME),
                new JobSeed("Data Engineer", Industry.ENGINEERING, EmploymentType.FULL_TIME),
                new JobSeed("Full Stack Developer", Industry.ENGINEERING, EmploymentType.FULL_TIME),
                new JobSeed("Design Lead", Industry.DESIGN, EmploymentType.FULL_TIME),
                new JobSeed("Sales Manager", Industry.SALES, EmploymentType.FULL_TIME),
                new JobSeed("Healthcare Analyst", Industry.HEALTHCARE, EmploymentType.CONTRACT),
                new JobSeed("Warehouse Manager", Industry.LOGISTICS, EmploymentType.FULL_TIME),
                new JobSeed("Business Development Intern", Industry.SALES, EmploymentType.INTERNSHIP),
                new JobSeed("Junior Frontend Developer", Industry.ENGINEERING, EmploymentType.INTERNSHIP),
                new JobSeed("Visual Designer", Industry.DESIGN, EmploymentType.FULL_TIME)
        );
        List<JobPosting> postings = new ArrayList<>();
        for (int i = 0; i < titles.size(); i++) {
            JobSeed seed = titles.get(i);
            EnterpriseProfile company = companies.get(i % companies.size());
            List<String> skills = IndianData.pickN(IndianData.SKILLS_BY_INDUSTRY.get(seed.industry()), IndianData.intBetween(3, 5));
            int salaryMin = IndianData.intBetween(6, 20);
            JobPosting posting = JobPosting.builder()
                    .enterprise(company).title(seed.title()).industry(seed.industry())
                    .location(IndianData.pick(NEIGHBORHOODS).name() + ", Hyderabad")
                    .remote(IndianData.RANDOM.nextDouble() < 0.35).employmentType(seed.type())
                    .salaryMin(salaryMin).salaryMax(salaryMin + IndianData.intBetween(4, 15))
                    .skills(skills)
                    .description(company.getCompanyName() + " is hiring a " + seed.title().toLowerCase() + " to join a growing team in Hyderabad.")
                    .status(IndianData.RANDOM.nextDouble() < 0.85 ? PostingStatus.OPEN : PostingStatus.PAUSED)
                    .build();
            posting.setDemoContent(true);
            postings.add(jobPostingRepository.save(posting));
        }
        return postings;
    }

    // §1.2's 15 applications at varied pipeline stages.
    private int seedApplicationsAndInterviews(List<CandidateProfile> candidates, List<JobPosting> postings, List<User> team) {
        List<JobPosting> open = postings.stream().filter(p -> p.getStatus() == PostingStatus.OPEN).toList();
        if (open.isEmpty()) return 0;
        List<ApplicationStage> stagePool = List.of(
                ApplicationStage.APPLIED, ApplicationStage.APPLIED, ApplicationStage.SCREENING,
                ApplicationStage.SCREENING, ApplicationStage.INTERVIEW, ApplicationStage.OFFER, ApplicationStage.REJECTED);
        int count = 0;
        for (int i = 0; i < 15 && i < candidates.size(); i++) {
            CandidateProfile candidate = candidates.get(i);
            JobPosting posting = IndianData.pick(open);
            if (applicationRepository.existsByCandidateIdAndJobPostingId(candidate.getId(), posting.getId())) continue;
            ApplicationStage stage = IndianData.pick(stagePool);
            Application application = Application.builder()
                    .candidate(candidate).jobPosting(posting).stage(stage)
                    .appliedAt(Instant.now().minus(Duration.ofDays(IndianData.intBetween(0, 10))))
                    .build();
            application.setDemoContent(true);
            application = applicationRepository.save(application);
            count++;

            if (stage == ApplicationStage.INTERVIEW || stage == ApplicationStage.OFFER) {
                List<InterviewSlot> slots = new ArrayList<>();
                Interview interview = Interview.builder().application(application).status(InterviewStatus.PROPOSED).build();
                for (int d = 1; d <= 3; d++) {
                    InterviewSlot slot = InterviewSlot.builder().interview(interview)
                            .start(Instant.now().plus(Duration.ofDays(d)).plus(Duration.ofHours(14)))
                            .durationMinutes(45).build();
                    slot.setDemoContent(true);
                    slots.add(slot);
                }
                interview.setProposedSlots(slots);
                interview.setDemoContent(true);
                if (stage == ApplicationStage.OFFER) interview.setStatus(InterviewStatus.CONFIRMED);
                interview = interviewRepository.save(interview);
                if (stage == ApplicationStage.OFFER && !interview.getProposedSlots().isEmpty()) {
                    interview.setConfirmedSlotId(interview.getProposedSlots().get(0).getId());
                    interviewRepository.save(interview);
                }
            }
        }
        return count;
    }

    // §1.2's 12 projects, 0-6 bids each, two fully resolved (won/lost visible).
    private record ProjectSeedResult(List<Project> projects, int bidCount) {}
    private ProjectSeedResult seedProjectsAndBids(List<EnterpriseProfile> companies, List<CandidateProfile> candidates) {
        record ProjectSeed(String title, Industry industry, int budgetMin, int budgetMax, int weeks, int bidCount) {}
        List<ProjectSeed> seeds = List.of(
                new ProjectSeed("Landing page redesign - short-term engagement", Industry.DESIGN, 30000, 60000, 3, 3),
                new ProjectSeed("Backend API cleanup and documentation", Industry.ENGINEERING, 40000, 80000, 4, 0),
                new ProjectSeed("Mobile app QA pass before launch", Industry.ENGINEERING, 25000, 50000, 2, 5),
                new ProjectSeed("Brand identity refresh", Industry.DESIGN, 50000, 100000, 5, 6),
                new ProjectSeed("Sales deck + pitch narrative for Series A", Industry.SALES, 20000, 40000, 2, 2),
                new ProjectSeed("Patient intake flow redesign", Industry.HEALTHCARE, 35000, 70000, 4, 0),
                new ProjectSeed("Warehouse inventory system audit", Industry.LOGISTICS, 30000, 55000, 3, 1),
                new ProjectSeed("Data pipeline migration to a new warehouse", Industry.ENGINEERING, 60000, 120000, 6, 4),
                new ProjectSeed("Illustration set for onboarding flow", Industry.DESIGN, 15000, 30000, 2, 3),
                new ProjectSeed("Cold outreach sequence + CRM setup", Industry.SALES, 18000, 35000, 2, 0),
                new ProjectSeed("Compliance audit for telemedicine flow", Industry.HEALTHCARE, 40000, 80000, 4, 2),
                new ProjectSeed("Route optimization proof-of-concept", Industry.LOGISTICS, 45000, 90000, 5, 1)
        );
        List<Project> projects = new ArrayList<>();
        int totalBids = 0;
        for (int i = 0; i < seeds.size(); i++) {
            ProjectSeed seed = seeds.get(i);
            EnterpriseProfile company = companies.get(i % companies.size());
            List<String> skills = IndianData.pickN(IndianData.SKILLS_BY_INDUSTRY.get(seed.industry()), IndianData.intBetween(3, 4));
            Project project = Project.builder()
                    .postedByUser(company.getUser()).title(seed.title())
                    .description("A focused, fixed-scope engagement for a " + seed.industry().wireValue().toLowerCase() + " specialist. Clear deliverables, fast turnaround.")
                    .budgetMin(seed.budgetMin()).budgetMax(seed.budgetMax()).durationWeeks(seed.weeks())
                    .skills(skills).status(ProjectStatus.OPEN)
                    .endsAt(Instant.now().plus(Duration.ofDays(IndianData.intBetween(5, 20))))
                    .build();
            project.setDemoContent(true);
            project = projectRepository.save(project);

            List<Bid> bids = new ArrayList<>();
            Set<CandidateProfile> bidders = new HashSet<>(IndianData.pickN(candidates, Math.min(seed.bidCount(), candidates.size())));
            for (CandidateProfile bidder : bidders) {
                int amount = seed.budgetMin() + IndianData.intBetween(0, Math.max(1, seed.budgetMax() - seed.budgetMin()));
                Bid bid = Bid.builder().project(project).bidderUser(bidder.getUser()).amount(amount)
                        .matchPercentage(IndianData.intBetween(60, 95)).agentPick(false).status(BidStatus.PENDING)
                        .submittedAt(Instant.now().minus(Duration.ofHours(IndianData.intBetween(1, 72))))
                        .build();
                bid.setDemoContent(true);
                bids.add(bidRepository.save(bid));
                totalBids++;
            }
            // §1.2 "two fully resolved so won/lost states are visible" - the first two projects
            // with bids get awarded.
            if (!bids.isEmpty() && i < 2) {
                Bid top = bids.stream().max((a, b) -> Integer.compare(a.getAmount(), b.getAmount())).orElseThrow();
                for (Bid b : bids) b.setStatus(b.getId().equals(top.getId()) ? BidStatus.WON : BidStatus.LOST);
                bidRepository.saveAll(bids);
                project.setStatus(ProjectStatus.AWARDED);
                project.setAwardedBidId(top.getId());
                projectRepository.save(project);

                List<String> labels = List.of("Kickoff & plan", "Midpoint delivery", "Final delivery");
                double[] split = {0.3, 0.4, 0.3};
                for (int m = 0; m < labels.size(); m++) {
                    int tranche = (int) Math.round(top.getAmount() * split[m]);
                    Milestone milestone = Milestone.builder().project(project).label(labels.get(m)).orderIndex(m)
                            .amount(tranche).status(m == 0 ? MilestoneStatus.ACCEPTED : MilestoneStatus.PENDING).build();
                    milestone.setDemoContent(true);
                    milestoneRepository.save(milestone);
                }
            }
            projects.add(project);
        }
        return new ProjectSeedResult(projects, totalBids);
    }

    private void seedNotifications(List<CandidateProfile> candidates, EnterpriseProfile company) {
        CandidateProfile recipient = candidates.get(0);
        record NotifSeed(NotificationType type, String title, String body) {}
        List<NotifSeed> seeds = List.of(
                new NotifSeed(NotificationType.AGENT, "Your agent found something", "Jenny noticed several activities near Gachibowli happening today that match your interests."),
                new NotifSeed(NotificationType.INTERVIEW, "Interview scheduled", company.getCompanyName() + " proposed a time for your interview."),
                new NotifSeed(NotificationType.BID, "New bid on your project", "A candidate placed a bid on your open project."),
                new NotifSeed(NotificationType.SYSTEM, "Complete your profile", "Add a bio and verify more skills to improve your career health score.")
        );
        for (NotifSeed seed : seeds) {
            var notification = notificationService.notify(recipient.getUser(), seed.type(), seed.title(), seed.body());
            notification.setDemoContent(true);
            notificationRepository.save(notification);
        }
    }

    private void seedFollows(List<CandidateProfile> candidates) {
        for (int i = 0; i < 15; i++) {
            CandidateProfile follower = candidates.get(i);
            CandidateProfile following = candidates.get((i + 7) % candidates.size());
            if (follower.getUser().getId().equals(following.getUser().getId())) continue;
            if (followRepository.existsByFollowerUserIdAndFollowingUserId(follower.getUser().getId(), following.getUser().getId())) continue;
            Follow follow = Follow.builder().followerUser(follower.getUser()).followingUser(following.getUser()).build();
            follow.setDemoContent(true);
            followRepository.save(follow);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Removal - FK-driven, not just demoContent-flag-driven, so it also cleans up anything a
    // real account did against seeded content while it was live (joined a demo activity,
    // applied to a demo job, bid on a demo project, followed a demo profile, messaged a demo
    // recruiter) - not just the rows the seeder itself created.
    // ---------------------------------------------------------------------------------------

    @Transactional
    public RemovalSummary removeAll() {
        List<Post> posts = postRepository.findByDemoContentTrue();
        List<CandidateProfile> candidates = candidateProfileRepository.findByDemoContentTrue();
        List<JobPosting> jobPostings = jobPostingRepository.findByDemoContentTrue();
        List<Project> projects = projectRepository.findByDemoContentTrue();
        List<EnterpriseProfile> companies = enterpriseProfileRepository.findByDemoContentTrue();

        Set<Room> rooms = new java.util.LinkedHashSet<>(roomRepository.findByDemoContentTrue());
        rooms.addAll(roomRepository.findByPost_DemoContentTrue());
        int roomCount = rooms.size();
        for (Room room : rooms) {
            roomMessageRepository.deleteByRoomId(room.getId());
            roomMemberRepository.deleteByRoomId(room.getId());
            moderationItemRepository.deleteByRoomId(room.getId());
        }
        roomRepository.deleteAll(rooms);

        for (Post post : posts) {
            postJoinRequestRepository.deleteByPostId(post.getId());
            postCommentRepository.deleteByPostId(post.getId());
            postReactionRepository.deleteByPostId(post.getId());
            postSaveRepository.deleteByPostId(post.getId());
            moderationItemRepository.deleteByPostId(post.getId());
        }
        postRepository.deleteAll(posts);

        for (Project project : projects) {
            bidRepository.deleteByProjectId(project.getId());
            milestoneRepository.deleteByProjectId(project.getId());
        }
        projectRepository.deleteAll(projects);

        for (JobPosting posting : jobPostings) {
            for (var application : applicationRepository.findByJobPosting(posting)) {
                interviewRepository.deleteByApplicationId(application.getId());
            }
            applicationRepository.deleteByJobPostingId(posting.getId());
        }
        jobPostingRepository.deleteAll(jobPostings);

        notificationRepository.deleteByDemoContentTrue();

        for (CandidateProfile candidate : candidates) {
            UUID userId = candidate.getUser().getId();
            followRepository.deleteByFollowerUserId(userId);
            followRepository.deleteByFollowingUserId(userId);
            shortlistEntryRepository.deleteByCandidateId(candidate.getId());
            unlockedCandidateRepository.deleteByCandidateId(candidate.getId());
        }
        candidateProfileRepository.deleteAll(candidates);

        for (EnterpriseProfile company : companies) {
            invitationRepository.deleteByTenantId(company.getId());
            membershipRepository.deleteByTenantId(company.getId());
        }
        enterpriseProfileRepository.deleteAll(companies);

        List<User> users = userRepository.findByDemoContentTrue();
        userRepository.deleteAll(users);

        log.info("Demo content removed: {} accounts, {} companies, {} posts, {} job postings, {} projects, {} rooms",
                users.size(), companies.size(), posts.size(), jobPostings.size(), projects.size(), roomCount);
        return new RemovalSummary(users.size(), companies.size(), posts.size(), jobPostings.size(), projects.size(), roomCount);
    }
}
