package com.vikisol.arena.seed;

import com.vikisol.arena.activity.entity.ActivityEventType;
import com.vikisol.arena.activity.service.ActivityService;
import com.vikisol.arena.applications.entity.Application;
import com.vikisol.arena.applications.entity.ApplicationStage;
import com.vikisol.arena.applications.repository.ApplicationRepository;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.enterprise.entity.CompanySize;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.entity.Membership;
import com.vikisol.arena.enterprise.entity.MembershipStatus;
import com.vikisol.arena.enterprise.entity.Plan;
import com.vikisol.arena.enterprise.entity.ShortlistEntry;
import com.vikisol.arena.enterprise.entity.UnlockedCandidate;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import com.vikisol.arena.enterprise.repository.MembershipRepository;
import com.vikisol.arena.enterprise.repository.ShortlistEntryRepository;
import com.vikisol.arena.enterprise.repository.UnlockedCandidateRepository;
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
import com.vikisol.arena.matching.ScoringService;
import com.vikisol.arena.follows.entity.Follow;
import com.vikisol.arena.follows.repository.FollowRepository;
import com.vikisol.arena.notifications.entity.NotificationType;
import com.vikisol.arena.notifications.service.NotificationService;
import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.entity.PostAudience;
import com.vikisol.arena.posts.entity.PostIntentType;
import com.vikisol.arena.posts.entity.PostJoinRequest;
import com.vikisol.arena.posts.entity.PostJoinStatus;
import com.vikisol.arena.posts.entity.PostStatus;
import com.vikisol.arena.posts.entity.PostVisibility;
import com.vikisol.arena.posts.repository.PostJoinRequestRepository;
import com.vikisol.arena.posts.repository.PostRepository;
import com.vikisol.arena.profile.entity.AutonomyLevel;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.entity.CandidateSkill;
import com.vikisol.arena.profile.entity.ConsentSettings;
import com.vikisol.arena.profile.entity.Industry;
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
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Populates the local database with realistic, Indian-context demo data on first startup so the
 * API returns something meaningful without any manual setup - mirrors the flavor of arena-web's
 * mock/seed.ts. Idempotent: skips entirely if any users already exist, so it only ever runs once
 * against a fresh database. Disable with SEED_ENABLED=false.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(value = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements ApplicationRunner {

    public static final String DEMO_TALENT_EMAIL = "demo.talent@vikisol.dev";
    public static final String DEMO_ENTERPRISE_EMAIL = "demo.enterprise@vikisol.dev";
    public static final String DEMO_PASSWORD = "Demo@12345";

    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final EnterpriseProfileRepository enterpriseProfileRepository;
    private final MembershipRepository membershipRepository;
    private final JobPostingRepository jobPostingRepository;
    private final ApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;
    private final ProjectRepository projectRepository;
    private final BidRepository bidRepository;
    private final MilestoneRepository milestoneRepository;
    private final ShortlistEntryRepository shortlistEntryRepository;
    private final UnlockedCandidateRepository unlockedCandidateRepository;
    private final NotificationService notificationService;
    private final ActivityService activityService;
    private final ScoringService scoringService;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final PostRepository postRepository;
    private final PostJoinRequestRepository postJoinRequestRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomMessageRepository roomMessageRepository;
    private final FollowRepository followRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Was userRepository.count() > 0 - broke on a genuinely fresh database (first hit
        // deploying to Railway staging) because RoleMigration.backfillDemoAccounts() runs first
        // (@Order(HIGHEST_PRECEDENCE)) and unconditionally seeds exactly one user, the platform
        // admin, regardless of whether DataSeeder has ever run. That made this guard see
        // count()==1 and skip all the rich demo data (companies, candidates, postings,
        // applications, interviews, marketplace) - it only ever worked locally because local dev
        // always ran against an already-populated-from-before-this-suite database, never a truly
        // empty one. EnterpriseProfile is something only DataSeeder itself ever creates, so it
        // can't be tripped by RoleMigration's unrelated seeding.
        if (enterpriseProfileRepository.count() > 0) {
            log.info("Seed data already present - skipping DataSeeder");
            return;
        }
        log.info("Seeding demo data into vikisol_arena...");

        List<EnterpriseProfile> companies = seedCompanies();
        List<CandidateProfile> candidates = seedCandidates();
        List<JobPosting> postings = seedJobPostings(companies);
        seedApplicationsAndInterviews(candidates, postings);
        seedMarketplace(companies, candidates);
        seedEnterpriseEngagement(companies.get(0), candidates);
        seedDemoActivityAndNotifications(candidates.get(0));
        seedPostsRoomsAndFollows(candidates);
        seedPlatformAdmin();

        log.info("Seed complete: {} companies, {} candidates, {} postings", companies.size(), candidates.size(), postings.size());
        log.info("Demo talent login: {} / {}", DEMO_TALENT_EMAIL, DEMO_PASSWORD);
        log.info("Demo company_admin login: {} / {}", DEMO_ENTERPRISE_EMAIL, DEMO_PASSWORD);
        log.info("Demo recruiter login: {} / {}", DEMO_RECRUITER_EMAIL, DEMO_PASSWORD);
        log.info("Demo hiring_manager login: {} / {}", DEMO_HIRING_MANAGER_EMAIL, DEMO_PASSWORD);
        log.info("Platform admin login: {} / {}", PLATFORM_ADMIN_EMAIL, DEMO_PASSWORD);
    }

    public static final String DEMO_RECRUITER_EMAIL = "demo.recruiter@vikisol.dev";
    public static final String DEMO_HIRING_MANAGER_EMAIL = "demo.hiringmanager@vikisol.dev";
    public static final String PLATFORM_ADMIN_EMAIL = "admin@vikisol.dev";

    private List<EnterpriseProfile> seedCompanies() {
        List<EnterpriseProfile> companies = new ArrayList<>();
        for (int i = 0; i < IndianData.COMPANIES.size(); i++) {
            IndianData.CompanySeed seed = IndianData.COMPANIES.get(i);
            boolean isDemo = i == 0;
            User user = userRepository.save(User.builder()
                    .email(isDemo ? DEMO_ENTERPRISE_EMAIL : ("hr@" + seed.name().toLowerCase().replaceAll("[^a-z]", "") + ".example.com"))
                    .passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                    .name(seed.name() + " Talent Team")
                    .role(Role.COMPANY_ADMIN)
                    .build());

            EnterpriseProfile profile = enterpriseProfileRepository.save(EnterpriseProfile.builder()
                    .user(user)
                    .companyName(seed.name())
                    .logoEmoji(seed.emoji())
                    .industry(IndianData.pick(IndianData.INDUSTRIES_LIST()))
                    .size(IndianData.pick(List.of(CompanySize.S_11_50, CompanySize.S_51_200, CompanySize.S_201_1000, CompanySize.S_1000_PLUS)))
                    .hiringFor(IndianData.pickN(List.of("Engineers", "Designers", "Sales reps", "Support staff"), IndianData.intBetween(1, 3)))
                    .plan(isDemo ? Plan.PRO : IndianData.pick(List.of(Plan.FREE, Plan.PRO, Plan.ENTERPRISE)))
                    .seatsUsed(isDemo ? 3 : IndianData.intBetween(1, 4))
                    .seatsTotal(isDemo ? 5 : IndianData.intBetween(3, 10))
                    .unlockCreditsUsed(0)
                    .unlockCreditsTotal(isDemo ? 25 : IndianData.intBetween(10, 30))
                    .build());
            membershipRepository.save(Membership.builder()
                    .user(user).tenant(profile).status(MembershipStatus.ACTIVE).joinedAt(user.getCreatedAt())
                    .build());
            companies.add(profile);

            if (isDemo) {
                seedDemoRecruiterAndHiringManager(user, profile);
            }
        }
        return companies;
    }

    // Gives the Company Admin console (CA1-CA7) and Hiring Manager lite (HM1-HM3) real,
    // multi-person tenant data to show against on the demo company from day one, rather than
    // only being exercisable by manually inviting someone through the UI first.
    // Existence-checked (like RoleMigration.backfillDemoAccounts()) - discovered live deploying
    // to Railway that these two idempotency guards can genuinely collide: RoleMigration runs
    // first and only backs these accounts in when it finds a pre-existing DEMO_ENTERPRISE_EMAIL
    // tenant, so it correctly no-ops on a truly fresh database, but a database that's gone
    // through a partial/failed earlier boot (e.g. a crashed deploy that got as far as
    // RoleMigration but not this far) can already have one of these rows present.
    private void seedDemoRecruiterAndHiringManager(User admin, EnterpriseProfile tenant) {
        if (userRepository.findByEmailIgnoreCase(DEMO_RECRUITER_EMAIL).isEmpty()) {
            User recruiter = userRepository.save(User.builder()
                    .email(DEMO_RECRUITER_EMAIL).passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                    .name("Priyanka Rao").role(Role.RECRUITER).build());
            membershipRepository.save(Membership.builder()
                    .user(recruiter).tenant(tenant).status(MembershipStatus.ACTIVE)
                    .invitedBy(admin).joinedAt(recruiter.getCreatedAt()).build());
        }

        if (userRepository.findByEmailIgnoreCase(DEMO_HIRING_MANAGER_EMAIL).isEmpty()) {
            User hiringManager = userRepository.save(User.builder()
                    .email(DEMO_HIRING_MANAGER_EMAIL).passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                    .name("Karthik Iyer").role(Role.HIRING_MANAGER).build());
            membershipRepository.save(Membership.builder()
                    .user(hiringManager).tenant(tenant).status(MembershipStatus.ACTIVE)
                    .invitedBy(admin).joinedAt(hiringManager.getCreatedAt()).build());
        }
    }

    // PA7: exactly one seeded platform admin account, credentials documented in the README - no
    // tenant, no EnterpriseProfile, /admin resolves nothing tenant-scoped for this user.
    private void seedPlatformAdmin() {
        if (userRepository.findByEmailIgnoreCase(PLATFORM_ADMIN_EMAIL).isPresent()) return;
        userRepository.save(User.builder()
                .email(PLATFORM_ADMIN_EMAIL).passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                .name("Vikisol Platform Admin").role(Role.PLATFORM_ADMIN).build());
    }

    private List<CandidateProfile> seedCandidates() {
        List<CandidateProfile> candidates = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            boolean isDemo = i == 0;
            Industry industry = isDemo ? Industry.ENGINEERING : IndianData.pick(IndianData.INDUSTRIES_LIST());
            int experienceYears = isDemo ? 5 : IndianData.intBetween(0, 14);
            String name = isDemo ? "Aarav Sharma" : IndianData.fullName();

            User user = userRepository.save(User.builder()
                    .email(isDemo ? DEMO_TALENT_EMAIL : ("candidate" + i + "@example.com"))
                    .passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                    .name(name)
                    .role(Role.TALENT)
                    .build());

            List<CandidateSkill> skills = IndianData.pickN(IndianData.SKILLS_BY_INDUSTRY.get(industry), IndianData.intBetween(3, 6)).stream()
                    .map(s -> new CandidateSkill(s, IndianData.RANDOM.nextDouble() < 0.5))
                    .toList();
            List<OpenTo> openTo = IndianData.pickN(List.of(OpenTo.FULL_TIME, OpenTo.CONTRACT, OpenTo.PROJECTS), IndianData.intBetween(1, 3));

            CandidateProfile profile = CandidateProfile.builder()
                    .user(user)
                    .name(name)
                    .avatarEmoji(IndianData.pick(IndianData.AVATAR_EMOJIS))
                    .title(IndianData.pick(IndianData.TITLES_BY_INDUSTRY.get(industry)))
                    .industry(industry)
                    .location(IndianData.pick(IndianData.LOCATIONS))
                    .remote(IndianData.RANDOM.nextDouble() < 0.4)
                    .skills(skills)
                    .experienceYears(experienceYears)
                    .rateFloor(IndianData.intBetween(6, 42))
                    .openTo(openTo)
                    .careerHealth(50)
                    .consent(new ConsentSettings(IndianData.RANDOM.nextDouble() < 0.7, IndianData.RANDOM.nextDouble() < 0.85))
                    .autonomy(IndianData.pick(List.of(AutonomyLevel.MANUAL, AutonomyLevel.SUPERVISED, AutonomyLevel.AUTOPILOT)))
                    .bio(experienceYears + "+ years in " + industry.wireValue().toLowerCase() + ", based in " + IndianData.pick(IndianData.LOCATIONS) + ".")
                    .build();
            profile.setCareerHealth(scoringService.computeCareerHealth(profile));
            candidates.add(candidateProfileRepository.save(profile));
        }
        return candidates;
    }

    private List<JobPosting> seedJobPostings(List<EnterpriseProfile> companies) {
        List<JobPosting> postings = new ArrayList<>();
        for (EnterpriseProfile company : companies) {
            int count = IndianData.intBetween(2, 4);
            for (int i = 0; i < count; i++) {
                Industry industry = company.getIndustry();
                List<String> skills = IndianData.pickN(IndianData.SKILLS_BY_INDUSTRY.get(industry), IndianData.intBetween(3, 6));
                int salaryMin = IndianData.intBetween(6, 20);
                JobPosting posting = jobPostingRepository.saveAndFlush(JobPosting.builder()
                        .enterprise(company)
                        .title(IndianData.pick(IndianData.TITLES_BY_INDUSTRY.get(industry)))
                        .industry(industry)
                        .location(IndianData.pick(IndianData.LOCATIONS))
                        .remote(IndianData.RANDOM.nextDouble() < 0.35)
                        .employmentType(IndianData.pick(List.of(EmploymentType.FULL_TIME, EmploymentType.CONTRACT, EmploymentType.INTERNSHIP)))
                        .salaryMin(salaryMin)
                        .salaryMax(salaryMin + IndianData.intBetween(4, 15))
                        .skills(skills)
                        .description("We're looking for a " + industry.wireValue().toLowerCase() + " professional to join our team and make an impact from day one.")
                        .status(IndianData.RANDOM.nextDouble() < 0.85 ? PostingStatus.OPEN : PostingStatus.PAUSED)
                        .build());
                backdate("arena_job_postings", posting.getId(), IndianData.intBetween(0, 21));
                postings.add(posting);
            }
        }
        return postings;
    }

    private void seedApplicationsAndInterviews(List<CandidateProfile> candidates, List<JobPosting> postings) {
        List<JobPosting> openPostings = postings.stream().filter(p -> p.getStatus() == PostingStatus.OPEN).toList();
        if (openPostings.isEmpty()) return;

        List<ApplicationStage> stagePool = List.of(
                ApplicationStage.APPLIED, ApplicationStage.APPLIED, ApplicationStage.SCREENING,
                ApplicationStage.SCREENING, ApplicationStage.INTERVIEW, ApplicationStage.OFFER, ApplicationStage.REJECTED);

        int applicantCount = Math.min(18, candidates.size());
        for (int i = 0; i < applicantCount; i++) {
            CandidateProfile candidate = candidates.get(i);
            JobPosting posting = IndianData.pick(openPostings);
            if (applicationRepository.existsByCandidateIdAndJobPostingId(candidate.getId(), posting.getId())) continue;

            ApplicationStage stage = i == 0 ? ApplicationStage.INTERVIEW : IndianData.pick(stagePool);
            Application application = applicationRepository.save(Application.builder()
                    .candidate(candidate).jobPosting(posting).stage(stage)
                    .appliedAt(Instant.now().minus(Duration.ofDays(IndianData.intBetween(0, 14))))
                    .build());

            if (stage == ApplicationStage.INTERVIEW || stage == ApplicationStage.OFFER) {
                List<InterviewSlot> slots = new ArrayList<>();
                Interview interview = Interview.builder().application(application).status(InterviewStatus.PROPOSED).build();
                for (int d = 1; d <= 3; d++) {
                    slots.add(InterviewSlot.builder().interview(interview)
                            .start(Instant.now().plus(Duration.ofDays(d)).plus(Duration.ofHours(14)))
                            .durationMinutes(45).build());
                }
                interview.setProposedSlots(slots);
                if (stage == ApplicationStage.OFFER) {
                    interview.setStatus(InterviewStatus.CONFIRMED);
                }
                interview = interviewRepository.save(interview);
                if (stage == ApplicationStage.OFFER && !interview.getProposedSlots().isEmpty()) {
                    interview.setConfirmedSlotId(interview.getProposedSlots().get(0).getId());
                    interviewRepository.save(interview);
                }
            }
        }
    }

    private void seedMarketplace(List<EnterpriseProfile> companies, List<CandidateProfile> candidates) {
        List<Project> projects = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            EnterpriseProfile company = IndianData.pick(companies);
            Industry industry = company.getIndustry();
            List<String> skills = IndianData.pickN(IndianData.SKILLS_BY_INDUSTRY.get(industry), IndianData.intBetween(3, 5));
            int budgetMin = IndianData.intBetween(2, 8) * 10000;
            Project project = projectRepository.save(Project.builder()
                    .postedByUser(company.getUser())
                    .title(IndianData.pick(IndianData.TITLES_BY_INDUSTRY.get(industry)) + " - short-term engagement")
                    .description("A focused, fixed-scope engagement for a " + industry.wireValue().toLowerCase() + " specialist. Clear deliverables, fast turnaround.")
                    .budgetMin(budgetMin)
                    .budgetMax(budgetMin + IndianData.intBetween(2, 6) * 10000)
                    .durationWeeks(IndianData.intBetween(2, 12))
                    .skills(skills)
                    .status(ProjectStatus.OPEN)
                    .endsAt(Instant.now().plus(Duration.ofDays(IndianData.intBetween(3, 20))))
                    .build());
            projects.add(project);
        }

        for (Project project : projects) {
            int bidCount = IndianData.intBetween(0, 5);
            List<Bid> bids = new ArrayList<>();
            Set<CandidateProfile> bidders = new HashSet<>(IndianData.pickN(candidates, bidCount));
            for (CandidateProfile bidder : bidders) {
                int amount = project.getBudgetMin() + IndianData.intBetween(0, Math.max(1, project.getBudgetMax() - project.getBudgetMin()));
                int matchPercentage = scoringService.computeMatchPercentage(bidder, new HashSet<>(project.getSkills()), null);
                bids.add(bidRepository.save(Bid.builder()
                        .project(project).bidderUser(bidder.getUser()).amount(amount).matchPercentage(matchPercentage)
                        .agentPick(matchPercentage > 85).status(BidStatus.PENDING)
                        .submittedAt(Instant.now().minus(Duration.ofDays(IndianData.intBetween(0, 5))))
                        .build()));
            }
            // Award the first project that actually received bids, for a non-empty "my projects
            // in progress" demo state - mirrors what awardProject() does in myProjects.ts.
            if (!bids.isEmpty() && projectRepository.findByStatus(ProjectStatus.AWARDED, org.springframework.data.domain.Pageable.unpaged()).isEmpty()) {
                Bid top = bids.stream().max((a, b) -> Integer.compare(a.getAmount(), b.getAmount())).orElseThrow();
                for (Bid b : bids) {
                    b.setStatus(b.getId().equals(top.getId()) ? BidStatus.WON : BidStatus.LOST);
                }
                bidRepository.saveAll(bids);
                project.setStatus(ProjectStatus.AWARDED);
                project.setAwardedBidId(top.getId());
                projectRepository.save(project);

                // Matches ProjectService.award()'s real 30/40/30 tranche split (mirrors arena-web's
                // MILESTONE_SPLIT in myProjects.ts) so seeded demo milestones carry the same
                // non-zero, coherent `amount` a real award would produce.
                List<String> labels = List.of("Kickoff & plan", "Midpoint delivery", "Final delivery");
                double[] split = {0.3, 0.4, 0.3};
                for (int i = 0; i < labels.size(); i++) {
                    int tranche = (int) Math.round(top.getAmount() * split[i]);
                    milestoneRepository.save(Milestone.builder().project(project).label(labels.get(i)).orderIndex(i)
                            .amount(tranche).status(i == 0 ? MilestoneStatus.ACCEPTED : MilestoneStatus.PENDING).build());
                }
            }
        }
    }

    private void seedEnterpriseEngagement(EnterpriseProfile demoEnterprise, List<CandidateProfile> candidates) {
        List<CandidateProfile> searchable = candidates.stream()
                .filter(c -> c.getConsent().isSearchableByEnterprises())
                .toList();
        for (CandidateProfile c : IndianData.pickN(searchable, Math.min(3, searchable.size()))) {
            shortlistEntryRepository.save(ShortlistEntry.builder().enterprise(demoEnterprise).candidate(c).build());
        }
        for (CandidateProfile c : IndianData.pickN(searchable, Math.min(2, searchable.size()))) {
            unlockedCandidateRepository.save(UnlockedCandidate.builder().enterprise(demoEnterprise).candidate(c).build());
            demoEnterprise.setUnlockCreditsUsed(demoEnterprise.getUnlockCreditsUsed() + 1);
        }
        enterpriseProfileRepository.save(demoEnterprise);
    }

    private void seedDemoActivityAndNotifications(CandidateProfile demoCandidate) {
        User user = demoCandidate.getUser();
        activityService.log(user, ActivityEventType.SCANNED, "Scanned new roles",
                "Your agent scanned 24 new postings matching your profile.", null, "Runs every morning based on your open-to preferences.", false);
        activityService.log(user, ActivityEventType.MATCH_FOUND, "Strong match found",
                "Found a role that matches your skills and rate expectations closely.", null, "Skill overlap and industry both matched.", true);
        notificationService.notify(user, NotificationType.AGENT, "Welcome to Vikisol Arena",
                "Your agent is live and scanning for roles that match your profile.");
        notificationService.notify(user, NotificationType.SYSTEM, "Complete your profile",
                "Add a bio and verify more skills to improve your career health score.");
    }

    // ARENA-V2-PRODUCT-ARCHITECTURE.md Phase A: a handful of demo Posts (mix of ACTIVITY/ASK/
    // UPDATE), some with an approved join -> a real Room with messages, plus a few Follows - so
    // the new Feed/Rooms surfaces aren't empty on first click-through, same purpose as this
    // class's other seed methods.
    private void seedPostsRoomsAndFollows(List<CandidateProfile> candidates) {
        CandidateProfile demo = candidates.get(0);
        User demoUser = demo.getUser();

        record PostSeed(PostIntentType intent, String body, String location, PostVisibility visibility, Integer capacity) {}
        List<PostSeed> seeds = List.of(
                new PostSeed(PostIntentType.ACTIVITY, "Badminton at 6pm today, Gachibowli - need 2 more for doubles", "Gachibowli", PostVisibility.PUBLIC, 4),
                new PostSeed(PostIntentType.ASK, "Anyone used a good freelance invoicing tool for Indian clients? Tired of manual GST calculations.", null, PostVisibility.PUBLIC, null),
                new PostSeed(PostIntentType.UPDATE, "Shipped a side project this weekend - a small habit tracker. First real users today!", null, null, null),
                new PostSeed(PostIntentType.ACTIVITY, "Weekend trek to Ananthagiri Hills, Saturday early morning - open to 5 people, first-timers welcome", "Ananthagiri Hills", PostVisibility.APPROVAL, 6),
                new PostSeed(PostIntentType.ASK, "Looking for a solid React Native mentor for a couple of hours a week - happy to pay for the time.", null, PostVisibility.APPROVAL, null)
        );

        List<Post> savedPosts = new ArrayList<>();
        for (int i = 0; i < seeds.size(); i++) {
            PostSeed seed = seeds.get(i);
            // First few authored by the demo talent account so its own Feed/Rooms views have
            // real "mine" data; the rest spread across other seeded candidates for a populated feed.
            CandidateProfile author = i < 2 ? demo : IndianData.pick(candidates.subList(1, candidates.size()));
            Post post = postRepository.save(Post.builder()
                    .authorUser(author.getUser())
                    .intentType(seed.intent())
                    .body(seed.body())
                    .locationText(seed.location())
                    .audience(PostAudience.GLOBAL)
                    .visibility(seed.visibility() == null ? PostVisibility.PUBLIC : seed.visibility())
                    .capacity(seed.capacity())
                    .status(PostStatus.OPEN)
                    .build());
            backdate("arena_posts", post.getId(), IndianData.intBetween(0, 3));
            savedPosts.add(post);
        }

        // Approve a join on the first ACTIVITY post (savedPosts.get(0), PUBLIC visibility) so the
        // demo talent account (its author) has a real Room with messages to open on first visit.
        Post activityPost = savedPosts.get(0);
        CandidateProfile joiner = candidates.get(1);
        PostJoinRequest joinRequest = postJoinRequestRepository.save(PostJoinRequest.builder()
                .post(activityPost).user(joiner.getUser())
                .status(PostJoinStatus.APPROVED).decidedAt(Instant.now())
                .build());
        activityPost.setSpotsFilled(activityPost.getSpotsFilled() + 1);
        postRepository.save(activityPost);

        Room room = roomRepository.save(Room.builder().post(activityPost).build());
        roomMemberRepository.save(RoomMember.builder().room(room).user(activityPost.getAuthorUser()).role(RoomMemberRole.ADMIN).lastReadAt(Instant.now()).build());
        roomMemberRepository.save(RoomMember.builder().room(room).user(joiner.getUser()).role(RoomMemberRole.MEMBER).build());

        List<String> demoMessages = List.of(
                "Count me in - what time should we get there?",
                "6pm sharp, court's booked till 7:30. Bring your own racket if you have one.",
                "Perfect, see you there!"
        );
        User[] senders = { joiner.getUser(), activityPost.getAuthorUser(), joiner.getUser() };
        for (int i = 0; i < demoMessages.size(); i++) {
            roomMessageRepository.save(RoomMessage.builder().room(room).sender(senders[i]).content(demoMessages.get(i)).build());
        }

        // A pending join request on the APPROVAL-visibility trek post, so the demo account (if
        // it's that post's author) or at least some author has something in their join-requests
        // panel to act on. Kept separate from the auto-approved room above.
        Post trekPost = savedPosts.get(3);
        if (!trekPost.getAuthorUser().getId().equals(candidates.get(2).getUser().getId())) {
            postJoinRequestRepository.save(PostJoinRequest.builder()
                    .post(trekPost).user(candidates.get(2).getUser()).status(PostJoinStatus.PENDING).build());
        }

        // A few follow relationships radiating from the demo account both directions, so
        // /identity's followers/following section isn't empty.
        for (CandidateProfile c : IndianData.pickN(candidates.subList(1, candidates.size()), 3)) {
            followRepository.save(Follow.builder().followerUser(demoUser).followingUser(c.getUser()).build());
        }
        for (CandidateProfile c : IndianData.pickN(candidates.subList(1, candidates.size()), 2)) {
            if (!followRepository.existsByFollowerUserIdAndFollowingUserId(c.getUser().getId(), demoUser.getId())) {
                followRepository.save(Follow.builder().followerUser(c.getUser()).followingUser(demoUser).build());
            }
        }
    }

    private void backdate(String table, java.util.UUID id, int daysAgo) {
        if (daysAgo <= 0) return;
        jdbcTemplate.update("UPDATE " + table + " SET created_at = created_at - (? || ' days')::interval WHERE id = ?", daysAgo, id);
    }
}
