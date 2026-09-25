package com.vikisol.arena.seed;

import com.vikisol.arena.communities.entity.Community;
import com.vikisol.arena.communities.entity.CommunityMember;
import com.vikisol.arena.communities.entity.CommunityRole;
import com.vikisol.arena.communities.repository.CommunityMemberRepository;
import com.vikisol.arena.communities.repository.CommunityRepository;
import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.entity.PostIntentType;
import com.vikisol.arena.posts.repository.PostRepository;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Starter communities for Discuss (Phase 2) so it isn't empty on day one: six topic communities
 * owned by demo accounts, each with a handful of demo members, and every demo question sorted
 * into the one whose keywords it matches. Runs once - skipped whenever any demo community already
 * exists - and only when app.demo-content.enabled is on. Everything it creates is demoContent and
 * goes away with DemoContentService.removeAll().
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(value = "app.demo-content.enabled", havingValue = "true")
public class DemoCommunitySeeder {

    private record Seed(String slug, String name, String emoji, String description, List<String> keywords) {
    }

    private static final List<Seed> SEEDS = List.of(
            new Seed("hyderabad-local", "Hyderabad Local", "📍", "Recommendations, repairs, rentals and everyday questions about living in Hyderabad.",
                    List.of("plumber", "repair", "kondapur", "couch", "chair", "bicycle", "dslr", "rent", "moving")),
            new Seed("careers", "Careers & Job Switch", "💼", "Referrals, interviews, switching roles and growing your career.",
                    List.of("referral", "switching", "interview", "portfolio", "mentor", "visa", "product company")),
            new Seed("freelance", "Freelance & Side Gigs", "🧾", "Contracts, clients, invoicing and one-off gigs.",
                    List.of("freelance", "contract", "bookkeeper", "translator", "proofread", "gig", "invoic")),
            new Seed("startups", "Startups & Founders", "🚀", "Co-founders, early customers, fundraising and building something new.",
                    List.of("co-founder", "cofounder", "saas", "startup", "founder")),
            new Seed("sports-fitness", "Sports & Fitness", "🏸", "Find partners, teams and tips - badminton, cricket, running and more.",
                    List.of("running", "badminton", "cricket", "football", "gym", "cycling", "partner")),
            new Seed("learning", "Learning & Languages", "📚", "Study groups, language exchanges, books and courses.",
                    List.of("language", "exchange", "book", "study", "course", "learn", "telugu", "spanish")));

    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository memberRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final PostRepository postRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        if (!communityRepository.findByDemoContentTrue().isEmpty()) return;
        List<CandidateProfile> people = candidateProfileRepository.findByDemoContentTrue();
        if (people.isEmpty()) return; // no demo accounts to own them - nothing to seed yet

        java.util.Map<String, Community> bySlug = new java.util.HashMap<>();
        for (int i = 0; i < SEEDS.size(); i++) {
            Seed seed = SEEDS.get(i);
            if (communityRepository.existsBySlug(seed.slug())) continue; // a real one took the name
            Community community = Community.builder()
                    .slug(seed.slug()).name(seed.name()).emoji(seed.emoji()).description(seed.description())
                    .createdBy(people.get(i % people.size()).getUser())
                    .build();
            community.setDemoContent(true);
            community = communityRepository.save(community);
            bySlug.put(seed.slug(), community);
            // Owner plus a spread of members (3-8 per community, deterministic).
            int memberCount = Math.min(people.size(), 3 + (i * 3) % 6);
            for (int m = 0; m < memberCount; m++) {
                CandidateProfile person = people.get((i + m) % people.size());
                CommunityMember member = CommunityMember.builder()
                        .community(community).user(person.getUser())
                        .role(m == 0 ? CommunityRole.OWNER : CommunityRole.MEMBER)
                        .build();
                member.setDemoContent(true);
                memberRepository.save(member);
            }
        }

        int sorted = 0;
        for (Post post : postRepository.findByDemoContentTrue()) {
            if (post.getCommunity() != null || post.getIntentType() == PostIntentType.ACTIVITY) continue;
            String text = ((post.getTitle() == null ? "" : post.getTitle()) + " " + post.getBody()).toLowerCase(Locale.ROOT);
            for (Seed seed : SEEDS) {
                Community community = bySlug.get(seed.slug());
                if (community != null && seed.keywords().stream().anyMatch(text::contains)) {
                    post.setCommunity(community);
                    sorted++;
                    break;
                }
            }
        }
        log.info("DemoCommunitySeeder: created {} demo communities, sorted {} demo discussions into them", bySlug.size(), sorted);
    }
}
