package com.vikisol.arena.matching;

import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.entity.CandidateSkill;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Single source of truth for every "match %" and "career health" number shown anywhere in the
 * product - job match cards, marketplace bid match, Talent Universe search results, and the
 * candidate dashboard's career-health score. AUDIT.md flagged the mock frontend computing these
 * independently per-screen as a real data-coherence risk; every caller in this backend (jobs,
 * marketplace, enterprise search, profile) routes through this class instead of recomputing its
 * own formula.
 */
@Service
public class ScoringService {

    /** 0-100 composite score reflecting how complete/strong a candidate profile is. */
    public int computeCareerHealth(CandidateProfile profile) {
        int score = 20; // baseline for having an account at all

        if (profile.getBio() != null && !profile.getBio().isBlank()) score += 10;
        if (profile.getCvUrl() != null && !profile.getCvUrl().isBlank()) score += 10;

        List<CandidateSkill> skills = profile.getSkills();
        score += Math.min(20, skills.size() * 3);
        long verifiedCount = skills.stream().filter(CandidateSkill::isVerified).count();
        if (!skills.isEmpty()) {
            score += Math.round(20f * verifiedCount / skills.size());
        }

        score += Math.min(15, profile.getExperienceYears());

        if (profile.getOpenTo() != null && !profile.getOpenTo().isEmpty()) score += 10;
        if (profile.getConsent() != null && profile.getConsent().isSearchableByEnterprises()) score += 5;

        return Math.max(0, Math.min(100, score));
    }

    /** Match % between a candidate and a set of role/project requirements (skills + industry). */
    public int computeMatchPercentage(CandidateProfile candidate, Set<String> requiredSkills, String requiredIndustryWireValue) {
        Set<String> candidateSkills = candidate.getSkills().stream()
                .map(s -> s.getName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Set<String> normalizedRequired = requiredSkills.stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        int overlap = 0;
        for (String skill : normalizedRequired) {
            if (candidateSkills.contains(skill)) overlap++;
        }

        double skillRatio = normalizedRequired.isEmpty() ? 0.5 : (double) overlap / normalizedRequired.size();
        int base = 55 + (int) Math.round(skillRatio * 35); // 55-90 from skill overlap alone

        boolean industryMatches = requiredIndustryWireValue == null
                || requiredIndustryWireValue.equalsIgnoreCase(candidate.getIndustry().wireValue());
        if (industryMatches) base += 5;

        // Career-health nudges the score within a small band so two candidates with identical
        // skill overlap still differentiate slightly, without letting it dominate the number.
        base += Math.round((computeCareerHealth(candidate) - 50) / 10f);

        return Math.max(35, Math.min(99, base));
    }
}
