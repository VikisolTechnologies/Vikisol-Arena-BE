package com.vikisol.arena.seed;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.enterprise.entity.CompanySize;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.entity.Plan;
import com.vikisol.arena.profile.entity.AutonomyLevel;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.entity.ConsentSettings;
import com.vikisol.arena.profile.entity.Industry;
import com.vikisol.arena.profile.entity.OpenTo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the starter (blank) CandidateProfile/EnterpriseProfile a real signup gets before the
 * user has filled in an onboarding wizard - mirrors what arena-web's mock profile.ts does by
 * falling back to base defaults when no onboarding data exists yet. Also used by DataSeeder as
 * the base a bulk-seeded profile gets randomized on top of.
 */
@Component
public class SeedDataFactory {

    public CandidateProfile blankCandidateProfile(User user) {
        Industry industry = Industry.ENGINEERING;
        return CandidateProfile.builder()
                .user(user)
                .name(user.getName())
                .avatarEmoji(IndianData.pick(IndianData.AVATAR_EMOJIS))
                .title("New Talent")
                .industry(industry)
                .location("Bengaluru")
                .remote(false)
                // Genuinely mutable, independent ArrayLists - not List.of(). A real, latent bug
                // found live (2026-09-15) via DemoContentService: any later code that fetches an
                // already-persisted profile and calls repository.save() without first replacing
                // these @ElementCollection fields makes Hibernate try to .clear() the collection
                // it's still holding at merge time - an immutable backing list throws
                // UnsupportedOperationException with no message right there. Not hypothetical -
                // this is exactly what a never-onboarded blank profile (skills/openTo still at
                // their blankCandidateProfile() defaults) does the moment anything else on it
                // gets updated and saved.
                .skills(new ArrayList<>())
                .experienceYears(0)
                .rateFloor(6)
                .openTo(new ArrayList<>(List.of(OpenTo.FULL_TIME)))
                .careerHealth(20)
                .consent(new ConsentSettings(false, true))
                .autonomy(AutonomyLevel.SUPERVISED)
                .bio(null)
                .build();
    }

    public EnterpriseProfile blankEnterpriseProfile(User user) {
        return EnterpriseProfile.builder()
                .user(user)
                .companyName(user.getName() + "'s Company")
                .logoEmoji("🏢")
                .industry(Industry.ENGINEERING)
                .size(CompanySize.S_1_10)
                .hiringFor(new ArrayList<>())
                .plan(Plan.FREE)
                .seatsUsed(1)
                .seatsTotal(3)
                .unlockCreditsUsed(0)
                .unlockCreditsTotal(10)
                .build();
    }
}
