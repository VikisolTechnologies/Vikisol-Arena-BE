package com.vikisol.arena.seed;

import com.vikisol.arena.applications.repository.ApplicationRepository;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.geo.GeohashUtil;
import com.vikisol.arena.common.util.HandleGenerator;
import com.vikisol.arena.enterprise.entity.CompanySize;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.entity.Membership;
import com.vikisol.arena.enterprise.entity.MembershipStatus;
import com.vikisol.arena.enterprise.entity.Plan;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import com.vikisol.arena.enterprise.repository.MembershipRepository;
import com.vikisol.arena.enterprise.repository.ShortlistEntryRepository;
import com.vikisol.arena.enterprise.repository.UnlockedCandidateRepository;
import com.vikisol.arena.follows.repository.FollowRepository;
import com.vikisol.arena.interviews.repository.InterviewRepository;
import com.vikisol.arena.jobs.entity.EmploymentType;
import com.vikisol.arena.jobs.entity.JobPosting;
import com.vikisol.arena.jobs.entity.PostingStatus;
import com.vikisol.arena.jobs.repository.JobPostingRepository;
import com.vikisol.arena.marketplace.entity.Bid;
import com.vikisol.arena.marketplace.entity.BidStatus;
import com.vikisol.arena.marketplace.entity.Project;
import com.vikisol.arena.marketplace.entity.ProjectStatus;
import com.vikisol.arena.marketplace.repository.BidRepository;
import com.vikisol.arena.marketplace.repository.MilestoneRepository;
import com.vikisol.arena.marketplace.repository.ProjectRepository;
import com.vikisol.arena.notifications.entity.NotificationType;
import com.vikisol.arena.notifications.repository.NotificationRepository;
import com.vikisol.arena.notifications.service.NotificationService;
import com.vikisol.arena.platform.repository.ModerationItemRepository;
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
 * ARENA-WEB-AND-SEED.md Part 4 - an on-demand, labeled, fully-removable content overlay for
 * evaluating the v3 UI against realistic Hyderabad-flavored data, distinct from the original
 * DataSeeder bootstrap (which runs once on first boot, has no demoContent marker, and whose own
 * five seed Posts are now stale-dated - their startsAt values were relative to whenever that
 * seeder first ran, long since passed). Every entity this service creates gets
 * BaseEntity.demoContent = true; seed()/removeAll() are the "one documented command" each way -
 * exposed via DemoContentController, itself gated behind app.demo-content.enabled
 * (ARENA_SEED_MODE), off by default. See that class's own comment for why the whole controller
 * bean is absent, not just inert, when that flag is off.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DemoContentService {

    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final PostRepository postRepository;
    private final PostJoinRequestRepository postJoinRequestRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final RoomMessageRepository roomMessageRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final EnterpriseProfileRepository enterpriseProfileRepository;
    private final MembershipRepository membershipRepository;
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

    private static final String DEMO_PASSWORD = "DemoContent@Preview1";

    // Real Hyderabad IT-corridor neighborhoods, per ARENA-WEB-AND-SEED.md 4.3's own list.
    private record Neighborhood(String name, double lat, double lng) {}
    private static final List<Neighborhood> NEIGHBORHOODS = List.of(
            new Neighborhood("Gachibowli", 17.4400, 78.3489),
            new Neighborhood("Gopanapally", 17.4602, 78.3106),
            new Neighborhood("Madhapur", 17.4483, 78.3915),
            new Neighborhood("Kondapur", 17.4615, 78.3672)
    );

    public record SeedSummary(int candidates, int posts, int rooms, int jobPostings, int projects, int notifications) {}
    public record RemovalSummary(int candidates, int posts, int rooms, int jobPostings, int projects, int notifications) {}

    @Transactional
    public SeedSummary seed() {
        if (!postRepository.findByDemoContentTrue().isEmpty()) {
            log.info("Demo content already present - skipping seed (call removeAll() first to reseed)");
            var existing = postRepository.findByDemoContentTrue();
            return new SeedSummary(candidateProfileRepository.findByDemoContentTrue().size(), existing.size(),
                    roomRepository.findByDemoContentTrue().size(), jobPostingRepository.findByDemoContentTrue().size(),
                    projectRepository.findByDemoContentTrue().size(), 0);
        }

        List<CandidateProfile> candidates = seedCandidates();
        List<Post> posts = seedPosts(candidates);
        int roomCount = seedRoomsAndMessages(posts, candidates);
        EnterpriseProfile company = seedDemoCompany();
        List<JobPosting> jobPostings = seedJobPostings(company);
        List<Project> projects = seedProjects(company, candidates);
        int notificationCount = seedNotifications(candidates);

        log.info("Demo content seeded: {} candidates, {} posts, {} rooms, {} job postings, {} projects, {} notifications",
                candidates.size(), posts.size(), roomCount, jobPostings.size(), projects.size(), notificationCount);
        return new SeedSummary(candidates.size(), posts.size(), roomCount, jobPostings.size(), projects.size(), notificationCount);
    }

    // §4.3 "~15 user profiles with avatars, skills, outcome counts, varying account ages and
    // verification states." This entity's only avatar representation is avatarEmoji (no photo
    // URL field exists on CandidateProfile) - the same mechanism every real and previously-
    // seeded profile in this app already uses. Adding a new photo-URL column just for this
    // overlay would be schema expansion beyond what's needed (the new Home UI's own
    // ChampagneAvatar renders initials, never a photo, by deliberate design - see arena-web's
    // Home rebuild), so profiles get realistic emoji avatars, not stock photography - a scoped
    // trim from §4.3's literal wording, noted here rather than silently done.
    private List<CandidateProfile> seedCandidates() {
        List<CandidateProfile> candidates = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            String name = IndianData.fullName();
            Industry industry = IndianData.pick(IndianData.INDUSTRIES_LIST());
            int experienceYears = IndianData.intBetween(0, 12);
            Neighborhood home = IndianData.pick(NEIGHBORHOODS);

            User user = userRepository.save(withDemoFlag(User.builder()
                    .email("demo" + i + "." + System.nanoTime() + "@preview.arena.vikisol.dev")
                    .passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                    .name(name)
                    .role(Role.TALENT)
                    .handle(HandleGenerator.generate(name, userRepository::existsByHandle))
                    .dateOfBirth(java.time.LocalDate.now().minusYears(IndianData.intBetween(22, 40)))
                    .build()));

            List<CandidateSkill> skills = i == 14
                    // §4.4 edge case: one deliberately sparse profile, to test the thin state.
                    ? List.of()
                    : IndianData.pickN(IndianData.SKILLS_BY_INDUSTRY.get(industry), IndianData.intBetween(3, 6)).stream()
                        .map(s -> new CandidateSkill(s, IndianData.RANDOM.nextDouble() < 0.4))
                        .toList();

            double jitterLat = home.lat() + (IndianData.RANDOM.nextDouble() - 0.5) * 0.01;
            double jitterLng = home.lng() + (IndianData.RANDOM.nextDouble() - 0.5) * 0.01;
            String geohash = GeohashUtil.encode(jitterLat, jitterLng);
            double[] approx = GeohashUtil.decode(geohash);

            CandidateProfile profile = CandidateProfile.builder()
                    .user(user)
                    .name(name)
                    .avatarEmoji(IndianData.pick(IndianData.AVATAR_EMOJIS))
                    .title(i == 14 ? "New to Arena" : IndianData.pick(IndianData.TITLES_BY_INDUSTRY.get(industry)))
                    .industry(industry)
                    .location(home.name() + ", Hyderabad")
                    .remote(IndianData.RANDOM.nextDouble() < 0.3)
                    .skills(skills)
                    .experienceYears(i == 14 ? 0 : experienceYears)
                    .rateFloor(IndianData.intBetween(6, 35))
                    .openTo(IndianData.pickN(List.of(OpenTo.FULL_TIME, OpenTo.CONTRACT, OpenTo.PROJECTS), IndianData.intBetween(1, 2)))
                    .careerHealth(i == 14 ? 15 : IndianData.intBetween(35, 90))
                    .consent(new ConsentSettings(IndianData.RANDOM.nextDouble() < 0.6, true))
                    .autonomy(IndianData.pick(List.of(AutonomyLevel.MANUAL, AutonomyLevel.SUPERVISED, AutonomyLevel.AUTOPILOT)))
                    .bio(i == 14 ? null : experienceYears + "+ years in " + industry.wireValue().toLowerCase() + ", " + home.name() + ".")
                    .locationConsent(LocationConsent.PRECISE)
                    .geohash(geohash)
                    .approxLat(approx[0])
                    .approxLng(approx[1])
                    .build();
            profile.setDemoContent(true);
            candidates.add(candidateProfileRepository.save(profile));
        }
        return candidates;
    }

    // §4.3's ~12 activities + ~8 needs, §4.4's edge cases (one very long title, one zero-join
    // activity). All startsAt values are relative to NOW (seed-time), unlike the original
    // DataSeeder's posts - the whole point of this overlay is content that's still "today" when
    // whoever's reviewing actually looks.
    private List<Post> seedPosts(List<CandidateProfile> candidates) {
        record ActivitySeed(String body, Neighborhood where, PostVisibility visibility, Integer capacity, int startsInHours, String meetingPoint) {}
        List<ActivitySeed> activitySeeds = List.of(
                new ActivitySeed("Badminton doubles tonight, need 2 more - court's already booked", NEIGHBORHOODS.get(0), PostVisibility.PUBLIC, 4, 3, "Smash Badminton Academy, Gachibowli - Court 2"),
                new ActivitySeed("Morning cricket - 6-a-side, casual, all skill levels welcome", NEIGHBORHOODS.get(3), PostVisibility.PUBLIC, 12, 14, "Kondapur Community Ground, near the water tank"),
                new ActivitySeed("UI/UX design jam - bring a half-finished project, leave with feedback", NEIGHBORHOODS.get(1), PostVisibility.APPROVAL, 8, 5, "WeWork Gopanapally, 3rd floor breakout room"),
                new ActivitySeed("Weekend cycling meet - Durgam Cheruvu loop, easy pace, first-timers fine", NEIGHBORHOODS.get(0), PostVisibility.PUBLIC, 10, 40, "Durgam Cheruvu main gate, west entrance"),
                new ActivitySeed("React + TypeScript study group - working through a real codebase together", NEIGHBORHOODS.get(2), PostVisibility.PUBLIC, 6, 8, "Madhapur Public Library, 2nd floor study room"),
                new ActivitySeed("Startup weekend hackathon kickoff - form teams, pitch by Sunday", NEIGHBORHOODS.get(0), PostVisibility.APPROVAL, 30, 20, "T-Hub, Gachibowli - main auditorium"),
                new ActivitySeed("Sci-fi book club - this month's pick is a Ted Chiang collection", NEIGHBORHOODS.get(3), PostVisibility.PUBLIC, 8, 6, "Roastery Coffee House, Kondapur"),
                new ActivitySeed("Football, 5-a-side, turf's booked till 9", NEIGHBORHOODS.get(1), PostVisibility.PUBLIC, 10, 4, "Play Arena Turf, Gopanapally"),
                new ActivitySeed("Sunrise photography walk - HITEC City to Madhapur, bring any camera", NEIGHBORHOODS.get(2), PostVisibility.PUBLIC, 6, 60, "Cyber Towers main gate, HITEC City"),
                // §4.4 edge case: an activity with zero joins - nobody's said yes yet.
                new ActivitySeed("Chess meetup - bring a board if you have one, a few spares available", NEIGHBORHOODS.get(0), PostVisibility.APPROVAL, 8, 30, "Gachibowli Community Hall, room 4"),
                new ActivitySeed("Sunrise yoga - all levels, mats available to borrow", NEIGHBORHOODS.get(3), PostVisibility.PUBLIC, 15, 16, "Kondapur District Park, near the jogging track"),
                new ActivitySeed("Board games evening - Catan, Codenames, whatever people bring", NEIGHBORHOODS.get(1), PostVisibility.PUBLIC, 6, 7, "Community clubhouse, Gopanapally Phase 2")
        );

        record NeedSeed(String body) {}
        List<NeedSeed> needSeeds = List.of(
                new NeedSeed("Looking for a Figma expert to review a portfolio - 30 minutes, happy to pay"),
                new NeedSeed("Anyone have a spare badminton racket for tonight's game near Gachibowli?"),
                new NeedSeed("Good GST-compliant invoicing tool for freelancers? Tired of doing this by hand"),
                new NeedSeed("Need 2 more flatmates near Kondapur - IT professionals preferred, move-in this month"),
                new NeedSeed("Looking for a daily carpool partner, Gachibowli to Madhapur commute"),
                new NeedSeed("Searching for a Spring Boot mentor - a couple of hours a week, can pay"),
                new NeedSeed("Anyone selling a used standing desk in Hyderabad? Preferably Gachibowli area"),
                // §4.4 edge case: one very long post title/body.
                new NeedSeed("Need someone experienced in both React Native and native iOS to help debug a really "
                        + "specific animation performance issue that only shows up on older Android devices under "
                        + "memory pressure, happy to pay for a couple of hours of pairing this week if anyone's free")
        );

        List<Post> saved = new ArrayList<>();
        for (int i = 0; i < activitySeeds.size(); i++) {
            ActivitySeed seed = activitySeeds.get(i);
            CandidateProfile author = candidates.get(i % candidates.size());
            String geohash = GeohashUtil.encode(seed.where().lat(), seed.where().lng());
            double[] approx = GeohashUtil.decode(geohash);
            Post post = Post.builder()
                    .authorUser(author.getUser())
                    .intentType(PostIntentType.ACTIVITY)
                    .body(seed.body())
                    .locationText(seed.where().name())
                    .audience(PostAudience.GLOBAL)
                    .visibility(seed.visibility())
                    .capacity(seed.capacity())
                    .status(PostStatus.OPEN)
                    .startsAt(Instant.now().plus(Duration.ofHours(seed.startsInHours())))
                    .exactMeetingPoint(seed.meetingPoint())
                    .geohash(geohash)
                    .approxLat(approx[0])
                    .approxLng(approx[1])
                    .build();
            post.setDemoContent(true);
            saved.add(postRepository.save(post));
        }
        for (int i = 0; i < needSeeds.size(); i++) {
            CandidateProfile author = candidates.get((i + 3) % candidates.size());
            Post post = Post.builder()
                    .authorUser(author.getUser())
                    .intentType(PostIntentType.ASK)
                    .body(needSeeds.get(i).body())
                    .audience(PostAudience.GLOBAL)
                    .visibility(PostVisibility.PUBLIC)
                    .status(PostStatus.OPEN)
                    .build();
            post.setDemoContent(true);
            saved.add(postRepository.save(post));
        }
        return saved;
    }

    // §4.3 "~5 conversations" (a leaner 2 for this pass - group rooms, the type this data model
    // actually supports today; see removeAll()'s own note on why direct-message/bid-thread
    // conversation types aren't seeded - Room is 1:1 with Post in the current schema, a real
    // structural gap for Inbox screen 5, not something to paper over with fabricated rows here).
    // §4.4 edge case: one very long last message.
    private int seedRoomsAndMessages(List<Post> posts, List<CandidateProfile> candidates) {
        int[] roomPostIndexes = {0, 4}; // badminton (short thread) + study group (long last message)
        int roomCount = 0;
        for (int postIndex : roomPostIndexes) {
            Post post = posts.get(postIndex);
            CandidateProfile joiner = candidates.get((postIndex + 5) % candidates.size());
            if (joiner.getUser().getId().equals(post.getAuthorUser().getId())) {
                joiner = candidates.get((postIndex + 6) % candidates.size());
            }

            PostJoinRequest join = PostJoinRequest.builder()
                    .post(post).user(joiner.getUser())
                    .status(PostJoinStatus.APPROVED).decidedAt(Instant.now())
                    .build();
            join.setDemoContent(true);
            postJoinRequestRepository.save(join);
            post.setSpotsFilled(post.getSpotsFilled() + 1);
            postRepository.save(post);

            Room room = Room.builder().post(post).build();
            room.setDemoContent(true);
            room = roomRepository.save(room);

            RoomMember admin = RoomMember.builder().room(room).user(post.getAuthorUser()).role(RoomMemberRole.ADMIN).lastReadAt(Instant.now()).build();
            admin.setDemoContent(true);
            roomMemberRepository.save(admin);
            RoomMember member = RoomMember.builder().room(room).user(joiner.getUser()).role(RoomMemberRole.MEMBER).build();
            member.setDemoContent(true);
            roomMemberRepository.save(member);

            List<String> messages = postIndex == 0
                    ? List.of("Count me in - what time should we get there?", "6pm sharp, bring your own racket if you have one.", "Perfect, see you there!")
                    : List.of("Is this still happening today?", "Yes! Room 2nd floor, we've got the whole session booked.",
                        "One more thing before we start - if anyone hasn't already, it'd help a lot if you could skim through the "
                        + "three chapters on hooks and context we talked about last week, since today's session is going to build "
                        + "directly on that and we'd rather spend the time actually working through the tricky parts in the real "
                        + "codebase together instead of re-explaining the basics from scratch for whoever hasn't had a chance yet");
            User[] senders = { joiner.getUser(), post.getAuthorUser(), joiner.getUser() };
            for (int i = 0; i < messages.size(); i++) {
                RoomMessage msg = RoomMessage.builder().room(room).sender(senders[i]).content(messages.get(i)).build();
                msg.setDemoContent(true);
                roomMessageRepository.save(msg);
            }
            roomCount++;
        }
        return roomCount;
    }

    // A single lightweight demo company as FK backing for demo job postings/projects only -
    // ARENA-PHASE-1-BUILD.md's current run explicitly excludes company pages ("don't start...
    // company pages, and don't restyle them in passing"), so this is intentionally NOT §4.3's
    // "~4 company pages with banners and posts" - just enough of an EnterpriseProfile to satisfy
    // JobPosting.enterprise's required FK, not a company-page seeding deliverable in its own right.
    private EnterpriseProfile seedDemoCompany() {
        User admin = userRepository.save(withDemoFlag(User.builder()
                .email("demo.company." + System.nanoTime() + "@preview.arena.vikisol.dev")
                .passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                .name("Preview Labs Talent Team")
                .role(Role.COMPANY_ADMIN)
                .handle(HandleGenerator.generate("Preview Labs Talent Team", userRepository::existsByHandle))
                .build()));
        EnterpriseProfile profile = EnterpriseProfile.builder()
                .user(admin)
                .companyName("Preview Labs")
                .logoEmoji("🔶")
                .industry(Industry.ENGINEERING)
                .size(CompanySize.S_11_50)
                .hiringFor(List.of("Engineers", "Designers"))
                .plan(Plan.PRO)
                .seatsUsed(1)
                .seatsTotal(3)
                .unlockCreditsUsed(0)
                .unlockCreditsTotal(10)
                .build();
        profile.setDemoContent(true);
        profile = enterpriseProfileRepository.save(profile);
        Membership membership = Membership.builder().user(admin).tenant(profile).status(MembershipStatus.ACTIVE).joinedAt(admin.getCreatedAt()).build();
        membership.setDemoContent(true);
        membershipRepository.save(membership);
        return profile;
    }

    // §4.3 "~6 projects and jobs, with budgets and bid counts, some with existing bids" - a
    // leaner 4 job postings + 2 projects for this pass (Work screen isn't in this batch; see
    // this service's class comment). Real, varied, TODAY-relative where that matters (postings
    // don't carry a startsAt, so recency comes from createdAt alone, left at seed-time - no
    // backdating needed, unlike the original DataSeeder's older-looking postings).
    private List<JobPosting> seedJobPostings(EnterpriseProfile company) {
        record JobSeed(String title, Industry industry, String location, boolean remote, EmploymentType type, int salaryMin, int salaryMax) {}
        List<JobSeed> seeds = List.of(
                new JobSeed("Frontend Developer", Industry.ENGINEERING, "Gachibowli, Hyderabad", false, EmploymentType.FULL_TIME, 8, 16),
                new JobSeed("Product Designer", Industry.DESIGN, "Madhapur, Hyderabad", true, EmploymentType.FULL_TIME, 10, 18),
                new JobSeed("DevOps Engineer", Industry.ENGINEERING, "Kondapur, Hyderabad", false, EmploymentType.CONTRACT, 12, 22),
                new JobSeed("Business Development Intern", Industry.SALES, "Gopanapally, Hyderabad", false, EmploymentType.INTERNSHIP, 2, 4)
        );
        List<JobPosting> postings = new ArrayList<>();
        for (JobSeed seed : seeds) {
            List<String> skills = IndianData.pickN(IndianData.SKILLS_BY_INDUSTRY.get(seed.industry()), IndianData.intBetween(3, 5));
            JobPosting posting = JobPosting.builder()
                    .enterprise(company)
                    .title(seed.title())
                    .industry(seed.industry())
                    .location(seed.location())
                    .remote(seed.remote())
                    .employmentType(seed.type())
                    .salaryMin(seed.salaryMin())
                    .salaryMax(seed.salaryMax())
                    .skills(skills)
                    .description("Preview Labs is hiring a " + seed.title().toLowerCase() + " to join a small, fast-moving team in Hyderabad.")
                    .status(PostingStatus.OPEN)
                    .build();
            posting.setDemoContent(true);
            postings.add(jobPostingRepository.save(posting));
        }
        return postings;
    }

    private List<Project> seedProjects(EnterpriseProfile company, List<CandidateProfile> candidates) {
        record ProjectSeed(String title, Industry industry, int budgetMin, int budgetMax, int weeks, int bidCount) {}
        List<ProjectSeed> seeds = List.of(
                new ProjectSeed("Landing page redesign - short-term engagement", Industry.DESIGN, 30000, 60000, 3, 3),
                new ProjectSeed("Backend API cleanup and documentation", Industry.ENGINEERING, 40000, 80000, 4, 0)
        );
        List<Project> projects = new ArrayList<>();
        for (ProjectSeed seed : seeds) {
            List<String> skills = IndianData.pickN(IndianData.SKILLS_BY_INDUSTRY.get(seed.industry()), IndianData.intBetween(3, 4));
            Project project = Project.builder()
                    .postedByUser(company.getUser())
                    .title(seed.title())
                    .description("A focused, fixed-scope engagement. Clear deliverables, fast turnaround.")
                    .budgetMin(seed.budgetMin())
                    .budgetMax(seed.budgetMax())
                    .durationWeeks(seed.weeks())
                    .skills(skills)
                    .status(ProjectStatus.OPEN)
                    .endsAt(Instant.now().plus(Duration.ofDays(IndianData.intBetween(5, 20))))
                    .build();
            project.setDemoContent(true);
            project = projectRepository.save(project);

            Set<CandidateProfile> bidders = new HashSet<>(IndianData.pickN(candidates, Math.min(seed.bidCount(), candidates.size())));
            for (CandidateProfile bidder : bidders) {
                int amount = seed.budgetMin() + IndianData.intBetween(0, seed.budgetMax() - seed.budgetMin());
                Bid bid = Bid.builder()
                        .project(project).bidderUser(bidder.getUser()).amount(amount)
                        .matchPercentage(IndianData.intBetween(60, 95))
                        .agentPick(false).status(BidStatus.PENDING)
                        .submittedAt(Instant.now().minus(Duration.ofHours(IndianData.intBetween(1, 48))))
                        .build();
                bid.setDemoContent(true);
                bidRepository.save(bid);
            }
            projects.add(project);
        }
        return projects;
    }

    // §4.3 "notifications of each type" - one AGENT/INTERVIEW/BID/SYSTEM each, on the first
    // seeded candidate, so a reviewer signed into a demo account (or looking at the badge on any
    // seeded profile) sees the full set. notify() doesn't accept the demoContent flag directly
    // (it's the same real path a genuine notification takes) - re-saved immediately after with
    // the flag set, same pattern as everything else in this service.
    private int seedNotifications(List<CandidateProfile> candidates) {
        CandidateProfile recipient = candidates.get(0);
        record NotifSeed(NotificationType type, String title, String body) {}
        List<NotifSeed> seeds = List.of(
                new NotifSeed(NotificationType.AGENT, "Your agent found something", "Jenny noticed 3 activities near Gachibowli happening today that match your interests."),
                new NotifSeed(NotificationType.INTERVIEW, "Interview scheduled", "Preview Labs proposed a time for your Frontend Developer interview."),
                new NotifSeed(NotificationType.BID, "New bid on your project", "A candidate placed a bid on \"Landing page redesign\"."),
                new NotifSeed(NotificationType.SYSTEM, "Complete your profile", "Add a bio and verify more skills to improve your career health score.")
        );
        int count = 0;
        for (NotifSeed seed : seeds) {
            var notification = notificationService.notify(recipient.getUser(), seed.type(), seed.title(), seed.body());
            notification.setDemoContent(true);
            notificationRepository.save(notification);
            count++;
        }
        return count;
    }

    // Regular Lombok @Builder (not @SuperBuilder) ignores inherited BaseEntity fields, same
    // reasoning as BaseEntity's own class comment - set demoContent post-build, before save().
    private User withDemoFlag(User user) {
        user.setDemoContent(true);
        return user;
    }

    // --------------------------------------------------------------------------------------
    // Removal - "one documented command removes it completely." Dependency order: children
    // before parents, keyed on the FK (not the child row's own demoContent flag) wherever a real
    // user could plausibly have interacted with seeded content during the review window - see
    // each repository method's own comment for which scenario it covers.
    // --------------------------------------------------------------------------------------

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
            membershipRepository.deleteByTenantId(company.getId());
        }
        enterpriseProfileRepository.deleteAll(companies);

        List<User> users = userRepository.findByDemoContentTrue();
        userRepository.deleteAll(users);

        log.info("Demo content removed: {} candidates, {} posts, {} rooms, {} job postings, {} projects",
                candidates.size(), posts.size(), roomCount, jobPostings.size(), projects.size());
        return new RemovalSummary(candidates.size(), posts.size(), roomCount, jobPostings.size(), projects.size(), 0);
    }
}
