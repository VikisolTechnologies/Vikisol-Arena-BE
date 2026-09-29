package com.vikisol.arena.career.service;

import com.vikisol.arena.applications.repository.ApplicationRepository;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.career.dto.CareerDtos.*;
import com.vikisol.arena.career.entity.CareerEnums;
import com.vikisol.arena.career.entity.CareerEnums.*;
import com.vikisol.arena.career.entity.CareerProfile;
import com.vikisol.arena.career.repository.CareerProfileRepository;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.enterprise.service.EnterpriseProfileService;
import com.vikisol.arena.follows.repository.FollowRepository;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

// The career layer (G18-G21): one identity, opted into career when the person is ready. Nothing
// here is visible to anyone else until it's published, and pay is private unless they choose.
@Service
@RequiredArgsConstructor
public class CareerService {

    static final String CURRENCY = "INR";
    static final int MAX_LOCATIONS = 5;
    static final int MAX_LOCATION_LENGTH = 60;
    static final int MAX_COMPENSATION = 1_000_000_000;

    enum Audience { EMPLOYER, CONNECTION, NEIGHBOR }

    private final CareerProfileRepository careerRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final EnterpriseProfileService enterpriseProfileService;
    private final ApplicationRepository applicationRepository;

    @Transactional(readOnly = true)
    public CareerSelfView getMine(UUID userId) {
        return toSelf(careerRepository.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("You haven't opened your career profile yet")));
    }

    @Transactional
    public CareerSelfView setup(UUID userId, SetupRequest r) {
        CareerProfile career = careerRepository.findByUserId(userId).orElse(null);
        try {
            if (career == null) {
                if (r.intent() == null) throw new BadRequestException("intent is required the first time");
                career = CareerProfile.builder().user(requireUser(userId)).build();
            }
            if (r.intent() != null) career.setIntent(CareerEnums.parse(Intent.class, r.intent(), "intent"));
            if (r.desiredRole() != null) career.setDesiredRole(r.desiredRole().isBlank() ? null : limit(r.desiredRole().trim(), 100, "desiredRole"));
            if (r.experienceLevel() != null) career.setExperienceLevel(CareerEnums.parse(ExperienceLevel.class, r.experienceLevel(), "experienceLevel"));
            if (r.workMode() != null) career.setWorkMode(CareerEnums.parse(WorkMode.class, r.workMode(), "workMode"));
            if (r.noticePeriod() != null) career.setNoticePeriod(CareerEnums.parse(NoticePeriod.class, r.noticePeriod(), "noticePeriod"));
            if (r.compensationVisibility() != null) {
                career.setCompensationVisibility(CareerEnums.parse(CompensationVisibility.class, r.compensationVisibility(), "compensationVisibility"));
            }
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
        if (r.preferredLocations() != null) career.setPreferredLocations(new ArrayList<>(cleanLocations(r.preferredLocations())));
        if (r.expectedMin() != null) career.setExpectedMin(r.expectedMin());
        if (r.expectedMax() != null) career.setExpectedMax(r.expectedMax());
        validatePay(career);
        // Choosing to explore quietly takes the profile out of view.
        if (career.getIntent() == Intent.EXPLORE_QUIETLY) {
            career.setPublishedAt(null);
            career.setOpenToWork(false);
        }
        return toSelf(careerRepository.save(career));
    }

    @Transactional
    public CareerSelfView publish(UUID userId, Boolean openToWork) {
        CareerProfile career = careerRepository.findByUserId(userId).orElseThrow(() -> new BadRequestException("Set up your career profile first"));
        if (career.getIntent() == Intent.EXPLORE_QUIETLY) {
            throw new BadRequestException("You chose to explore quietly. Change that to publish your career profile.");
        }
        career.setPublishedAt(Instant.now());
        if (openToWork != null) career.setOpenToWork(openToWork);
        return toSelf(careerRepository.save(career));
    }

    @Transactional
    public CareerSelfView unpublish(UUID userId) {
        CareerProfile career = careerRepository.findByUserId(userId).orElseThrow(() -> new BadRequestException("Set up your career profile first"));
        career.setPublishedAt(null);
        career.setOpenToWork(false);
        return toSelf(careerRepository.save(career));
    }

    // G20: built by the same code that serves other people, so the preview can't drift from
    // what they really get.
    @Transactional(readOnly = true)
    public PrivacyPreview preview(UUID userId) {
        CareerProfile career = careerRepository.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("You haven't opened your career profile yet"));
        List<String> skills = skillsOf(userId);
        String payTo = switch (career.getCompensationVisibility()) {
            case PRIVATE -> "nobody";
            case ON_APPLICATION -> "employers you apply to";
            case EMPLOYERS -> "employers";
        };
        return new PrivacyPreview(career.getPublishedAt() != null, payTo,
                view(career, Audience.EMPLOYER, skills, career.getCompensationVisibility() == CompensationVisibility.EMPLOYERS),
                view(career, Audience.CONNECTION, skills, false),
                view(career, Audience.NEIGHBOR, skills, false));
    }

    // G19: someone else's career profile, as the caller's audience sees it. Unpublished (or
    // never opened) is simply not found - no hint that it exists.
    @Transactional(readOnly = true)
    public CareerPublicView get(UUID viewerId, UUID userId) {
        if (viewerId.equals(userId)) throw new BadRequestException("Use /career/me for your own profile");
        CareerProfile career = careerRepository.findByUserId(userId)
                .filter(c -> c.getPublishedAt() != null)
                .orElseThrow(() -> new ResourceNotFoundException("No career profile"));
        User viewer = requireUser(viewerId);
        Audience audience = switch (viewer.getRole()) {
            case RECRUITER, COMPANY_ADMIN, HIRING_MANAGER -> Audience.EMPLOYER;
            default -> followRepository.existsByFollowerUserIdAndFollowingUserId(viewerId, userId)
                    && followRepository.existsByFollowerUserIdAndFollowingUserId(userId, viewerId)
                    ? Audience.CONNECTION : Audience.NEIGHBOR;
        };
        boolean pay = false;
        if (audience == Audience.EMPLOYER) {
            var tenant = enterpriseProfileService.findEntityForUser(viewerId).orElse(null);
            var candidate = candidateProfileRepository.findByUserId(userId).orElse(null);
            boolean applied = tenant != null && candidate != null
                    && applicationRepository.existsByCandidateIdAndJobPostingEnterpriseId(candidate.getId(), tenant.getId());
            pay = CompensationPolicy.employerMaySee(career, applied, false);
        }
        return view(career, audience, skillsOf(userId), pay);
    }

    // Who sees what:
    // employers   - role, level, work mode, locations, notice period, open to work, skills; pay only per CompensationPolicy
    // connections - role, level, work mode, open to work, skills (people you both follow)
    // neighbors   - role, open to work, skills
    private CareerPublicView view(CareerProfile c, Audience audience, List<String> skills, boolean pay) {
        boolean employer = audience == Audience.EMPLOYER;
        boolean closer = employer || audience == Audience.CONNECTION;
        return new CareerPublicView(c.getUser().getId().toString(), audience.name().toLowerCase(),
                c.getDesiredRole(),
                closer ? CareerEnums.wire(c.getExperienceLevel()) : null,
                closer ? CareerEnums.wire(c.getWorkMode()) : null,
                employer ? List.copyOf(c.getPreferredLocations()) : null,
                employer ? CareerEnums.wire(c.getNoticePeriod()) : null,
                c.isOpenToWork(),
                skills,
                pay ? c.getExpectedMin() : null,
                pay ? c.getExpectedMax() : null,
                pay && (c.getExpectedMin() != null || c.getExpectedMax() != null) ? CURRENCY : null);
    }

    private CareerSelfView toSelf(CareerProfile c) {
        return new CareerSelfView(CareerEnums.wire(c.getIntent()), c.getDesiredRole(), CareerEnums.wire(c.getExperienceLevel()),
                CareerEnums.wire(c.getWorkMode()), List.copyOf(c.getPreferredLocations()), CareerEnums.wire(c.getNoticePeriod()),
                CareerEnums.wire(c.getCompensationVisibility()), c.getExpectedMin(), c.getExpectedMax(), CURRENCY,
                c.isOpenToWork(), c.getPublishedAt() != null, c.getPublishedAt() == null ? null : c.getPublishedAt().toString());
    }

    private List<String> skillsOf(UUID userId) {
        return candidateProfileRepository.findByUserId(userId)
                .map(CandidateProfile::getSkills).map(s -> s.stream().map(k -> k.getName()).toList())
                .orElse(List.of());
    }

    private static List<String> cleanLocations(List<String> raw) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (String l : raw) {
            String v = l == null ? "" : l.trim();
            if (v.isEmpty()) continue;
            limit(v, MAX_LOCATION_LENGTH, "Each location");
            out.putIfAbsent(v.toLowerCase(Locale.ROOT), v);
        }
        if (out.size() > MAX_LOCATIONS) throw new BadRequestException("You can add up to " + MAX_LOCATIONS + " locations");
        return List.copyOf(out.values());
    }

    private static void validatePay(CareerProfile c) {
        for (Integer v : new Integer[]{c.getExpectedMin(), c.getExpectedMax()}) {
            if (v != null && (v < 0 || v > MAX_COMPENSATION)) throw new BadRequestException("Compensation must be between 0 and " + MAX_COMPENSATION);
        }
        if (c.getExpectedMin() != null && c.getExpectedMax() != null && c.getExpectedMin() > c.getExpectedMax()) {
            throw new BadRequestException("The minimum can't be more than the maximum");
        }
    }

    private static String limit(String value, int max, String label) {
        if (value.length() > max) throw new BadRequestException(label + " can be at most " + max + " characters");
        return value;
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }
}
