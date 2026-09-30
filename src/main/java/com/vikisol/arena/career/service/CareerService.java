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
import com.vikisol.arena.common.intake.IntakeAnswers;
import com.vikisol.arena.enterprise.service.EnterpriseProfileService;
import com.vikisol.arena.follows.repository.FollowRepository;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

// The career layer (G18-G21, and ARENA-APP-FLOW §6): one identity, opted into career when the
// person is ready. Nothing here is visible to anyone else until it's published (or, for one
// employer, until the person applies to them), each extra field has its own visibility, and pay
// is shared only per application ("include my CTC").
@Service
@RequiredArgsConstructor
public class CareerService {

    static final String CURRENCY = "INR";
    static final int MAX_LOCATIONS = 5;
    static final int MAX_LOCATION_LENGTH = 60;
    static final int MAX_COMPENSATION = 1_000_000_000;

    static final int MAX_SKILLS = 20;
    static final int MAX_LINKS = 5;

    enum Audience { EMPLOYER, CONNECTION, NEIGHBOR }

    // Row 19 visibility keys, and their defaults. Pay is always only_me: it's shared per
    // application instead.
    static final Map<String, FieldVisibility> DEFAULT_VISIBILITY;
    static final Set<String> PAY_KEYS = Set.of("currentCtc", "expectedCtc");

    static {
        Map<String, FieldVisibility> d = new LinkedHashMap<>();
        for (String k : List.of("status", "noticePeriod", "experienceMonths", "roleFamily", "skills", "sapModules", "certifications",
                "desiredRoles", "workModes", "preferredLocations", "relocate", "shift", "companySizes", "links", "education", "languages",
                "negotiable")) {
            d.put(k, FieldVisibility.PUBLIC);
        }
        d.put("currentCompany", FieldVisibility.EMPLOYERS_I_APPLY);
        d.put("lastWorkingDay", FieldVisibility.EMPLOYERS_I_APPLY);
        d.put("currentCtc", FieldVisibility.ONLY_ME);
        d.put("expectedCtc", FieldVisibility.ONLY_ME);
        DEFAULT_VISIBILITY = Collections.unmodifiableMap(d);
    }

    private final CareerProfileRepository careerRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final EnterpriseProfileService enterpriseProfileService;
    private final ApplicationRepository applicationRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public CareerSelfView getMine(UUID userId) {
        return toSelf(careerRepository.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("You haven't opened your career profile yet")));
    }

    @Transactional
    public CareerSelfView setup(UUID userId, SetupRequest r) {
        CareerProfile career = careerRepository.findByUserId(userId).orElse(null);
        Stored stored;
        try {
            if (career == null) {
                if (r.intent() == null) throw new BadRequestException("intent is required the first time");
                career = CareerProfile.builder().user(requireUser(userId)).build();
            }
            stored = readDetails(career);
            if (r.intent() != null) career.setIntent(CareerEnums.parse(Intent.class, r.intent(), "intent"));
            if (r.desiredRole() != null) career.setDesiredRole(r.desiredRole().isBlank() ? null : limit(r.desiredRole().trim(), 100, "desiredRole"));
            if (r.experienceLevel() != null) career.setExperienceLevel(CareerEnums.parse(ExperienceLevel.class, r.experienceLevel(), "experienceLevel"));
            if (r.workMode() != null) career.setWorkMode(CareerEnums.parse(WorkMode.class, r.workMode(), "workMode"));
            if (r.noticePeriod() != null) career.setNoticePeriod(r.noticePeriod().isBlank() ? null : CareerEnums.parseNotice(r.noticePeriod()));
            if (r.compensationVisibility() != null
                    && CareerEnums.parse(CompensationVisibility.class, r.compensationVisibility(), "compensationVisibility") != CompensationVisibility.PRIVATE) {
                throw new BadRequestException("Your pay stays private. You can include it on an application when you apply.");
            }
            applyExtras(career, stored, r);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
        if (r.preferredLocations() != null) career.setPreferredLocations(new ArrayList<>(cleanLocations(r.preferredLocations())));
        if (r.expectedMin() != null) career.setExpectedMin(r.expectedMin());
        if (r.expectedMax() != null) career.setExpectedMax(r.expectedMax());
        if (r.expectedCtc() != null) {
            career.setExpectedMin(r.expectedCtc().min());
            career.setExpectedMax(r.expectedCtc().max());
        }
        validatePay(career);
        career.setDetailsJson(writeJson(stored));
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
        return new PrivacyPreview(career.getPublishedAt() != null, "only employers you choose to include it for when you apply",
                view(career, Audience.EMPLOYER, skills, false, false),
                view(career, Audience.CONNECTION, skills, false, false),
                view(career, Audience.NEIGHBOR, skills, false, false),
                view(career, Audience.EMPLOYER, skills, true, false));
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
        boolean applied = false, pay = false;
        if (audience == Audience.EMPLOYER) {
            var tenant = enterpriseProfileService.findEntityForUser(viewerId).orElse(null);
            var candidate = candidateProfileRepository.findByUserId(userId).orElse(null);
            applied = tenant != null && candidate != null
                    && applicationRepository.existsByCandidateIdAndJobPostingEnterpriseId(candidate.getId(), tenant.getId());
            pay = applied && CompensationPolicy.employerMaySee(applicationRepository.ctcSharedWithEnterprise(candidate.getId(), tenant.getId()));
        }
        return view(career, audience, skillsOf(userId), applied, pay);
    }

    // Row 32: what an employer sees of an applicant's career profile. Applying shares it with that
    // employer (published or not), minus only-me fields; pay only if this application includes it.
    public CareerPublicView forApplicant(CareerProfile career, boolean includeCtc, List<String> skills) {
        if (career == null) return null;
        return view(career, Audience.EMPLOYER, skills, true, CompensationPolicy.employerMaySee(includeCtc));
    }

    // Who sees what:
    // employers   - role, level, work mode, locations, notice period, open to work, skills
    // connections - role, level, work mode, open to work, skills (people you both follow)
    // neighbors   - role, open to work, skills
    // Each row 19 field also has its own visibility (public / employers_i_apply / only_me); pay
    // only per CompensationPolicy.
    private CareerPublicView view(CareerProfile c, Audience audience, List<String> skills, boolean appliedHere, boolean pay) {
        boolean employer = audience == Audience.EMPLOYER;
        boolean closer = employer || audience == Audience.CONNECTION;
        Map<String, FieldVisibility> vis = visibilityOf(c);
        java.util.function.Predicate<String> may = key -> switch (vis.get(key)) {
            case PUBLIC -> true;
            case EMPLOYERS_I_APPLY -> employer && appliedHere;
            case ONLY_ME -> false;
        };
        return new CareerPublicView(c.getUser().getId().toString(), audience.name().toLowerCase(),
                c.getDesiredRole(),
                closer ? CareerEnums.wire(c.getExperienceLevel()) : null,
                closer ? CareerEnums.wire(c.getWorkMode()) : null,
                employer && may.test("preferredLocations") ? List.copyOf(c.getPreferredLocations()) : null,
                employer && may.test("noticePeriod") ? CareerEnums.wire(c.getNoticePeriod()) : null,
                c.isOpenToWork(),
                skills,
                pay ? c.getExpectedMin() : null,
                pay ? c.getExpectedMax() : null,
                pay && (c.getExpectedMin() != null || c.getExpectedMax() != null) ? CURRENCY : null,
                details(c, may, pay));
    }

    private CareerDetails details(CareerProfile c, java.util.function.Predicate<String> may, boolean pay) {
        Stored d = readDetails(c);
        return new CareerDetails(
                may.test("currentCompany") ? c.getCurrentCompany() : null,
                may.test("status") ? CareerEnums.wire(c.getWorkStatus()) : null,
                may.test("lastWorkingDay") && c.getLastWorkingDay() != null ? c.getLastWorkingDay().toString() : null,
                may.test("experienceMonths") ? c.getExperienceMonths() : null,
                may.test("roleFamily") ? c.getRoleFamily() : null,
                may.test("skills") ? emptyToNull(d.skills) : null,
                may.test("sapModules") ? emptyToNull(d.sapModules) : null,
                may.test("certifications") ? emptyToNull(d.certifications) : null,
                pay && (c.getCurrentCtcFixed() != null || c.getCurrentCtcVariable() != null)
                        ? new CurrentCtc(c.getCurrentCtcFixed(), c.getCurrentCtcVariable()) : null,
                pay && (c.getExpectedMin() != null || c.getExpectedMax() != null) ? new ExpectedCtc(c.getExpectedMin(), c.getExpectedMax()) : null,
                may.test("negotiable") ? c.getNegotiable() : null,
                may.test("desiredRoles") ? emptyToNull(d.desiredRoles) : null,
                may.test("workModes") ? emptyToNull(d.workModes) : null,
                may.test("relocate") ? d.relocate : null,
                may.test("shift") ? d.shift : null,
                may.test("companySizes") ? emptyToNull(d.companySizes) : null,
                may.test("links") ? emptyToNull(d.links) : null,
                may.test("education") ? d.education : null,
                may.test("languages") ? emptyToNull(d.languages) : null);
    }

    private CareerSelfView toSelf(CareerProfile c) {
        Map<String, String> vis = new LinkedHashMap<>();
        visibilityOf(c).forEach((k, v) -> vis.put(k, CareerEnums.wire(v)));
        return new CareerSelfView(CareerEnums.wire(c.getIntent()), c.getDesiredRole(), CareerEnums.wire(c.getExperienceLevel()),
                CareerEnums.wire(c.getWorkMode()), List.copyOf(c.getPreferredLocations()), CareerEnums.wire(c.getNoticePeriod()),
                CareerEnums.wire(c.getCompensationVisibility()), c.getExpectedMin(), c.getExpectedMax(), CURRENCY,
                c.isOpenToWork(), c.getPublishedAt() != null, c.getPublishedAt() == null ? null : c.getPublishedAt().toString(),
                details(c, key -> true, true), vis);
    }

    // --- row 19 extras ---------------------------------------------------------------------

    // The list and text extras kept as JSON (details_json).
    static class Stored {
        public List<SkillEntry> skills = new ArrayList<>();
        public List<String> sapModules = new ArrayList<>();
        public List<String> certifications = new ArrayList<>();
        public List<String> desiredRoles = new ArrayList<>();
        public List<String> workModes = new ArrayList<>();
        public Boolean relocate;
        public String shift;
        public List<String> companySizes = new ArrayList<>();
        public List<String> links = new ArrayList<>();
        public Education education;
        public List<String> languages = new ArrayList<>();
    }

    private void applyExtras(CareerProfile c, Stored d, SetupRequest r) {
        if (r.currentCompany() != null) c.setCurrentCompany(r.currentCompany().isBlank() ? null : limit(r.currentCompany().trim(), 80, "currentCompany"));
        if (r.status() != null) c.setWorkStatus(r.status().isBlank() ? null : CareerEnums.parse(WorkStatus.class, r.status(), "status"));
        if (r.lastWorkingDay() != null) {
            try {
                c.setLastWorkingDay(r.lastWorkingDay().isBlank() ? null : java.time.LocalDate.parse(r.lastWorkingDay().trim()));
            } catch (java.time.format.DateTimeParseException e) {
                throw new BadRequestException("lastWorkingDay must be a date like 2026-10-31");
            }
        }
        if (r.experienceMonths() != null) {
            if (r.experienceMonths() < 0 || r.experienceMonths() > 600) throw new BadRequestException("experienceMonths must be between 0 and 600");
            c.setExperienceMonths(r.experienceMonths());
        }
        if (r.roleFamily() != null) c.setRoleFamily(r.roleFamily().isBlank() ? null : CareerEnums.pick(CareerEnums.ROLE_FAMILIES, r.roleFamily(), "roleFamily"));
        if (r.skills() != null) d.skills = cleanSkills(r.skills());
        if (r.sapModules() != null) d.sapModules = r.sapModules().stream().filter(x -> x != null && !x.isBlank())
                .map(x -> CareerEnums.pick(CareerEnums.SAP_MODULES, x, "sapModules")).distinct().toList();
        if (r.certifications() != null) d.certifications = IntakeAnswers.cleanList(r.certifications(), 10, 100, "certifications", "your career profile");
        if (r.currentCtc() != null) {
            c.setCurrentCtcFixed(pay(r.currentCtc().fixed()));
            c.setCurrentCtcVariable(pay(r.currentCtc().variable()));
        }
        if (r.negotiable() != null) c.setNegotiable(r.negotiable());
        if (r.desiredRoles() != null) {
            d.desiredRoles = IntakeAnswers.cleanList(r.desiredRoles(), 3, 100, "desiredRoles", "your career profile");
            if (!d.desiredRoles.isEmpty()) c.setDesiredRole(d.desiredRoles.get(0));
        }
        if (r.workModes() != null) {
            d.workModes = r.workModes().stream().filter(x -> x != null && !x.isBlank())
                    .map(x -> CareerEnums.wire(CareerEnums.parse(WorkMode.class, x, "workModes"))).distinct().toList();
            if (d.workModes.size() == 1) c.setWorkMode(CareerEnums.parse(WorkMode.class, d.workModes.get(0), "workModes"));
            else if (!d.workModes.isEmpty()) c.setWorkMode(WorkMode.ANY);
        }
        if (r.relocate() != null) d.relocate = r.relocate();
        if (r.shift() != null) d.shift = r.shift().isBlank() ? null : CareerEnums.wire(CareerEnums.parse(Shift.class, r.shift(), "shift"));
        if (r.companySizes() != null) d.companySizes = r.companySizes().stream().filter(x -> x != null && !x.isBlank())
                .map(x -> CareerEnums.pick(CareerEnums.COMPANY_SIZES, x, "companySizes")).distinct().toList();
        if (r.links() != null) {
            d.links = IntakeAnswers.cleanList(r.links(), MAX_LINKS, 300, "links", "your career profile");
            for (String link : d.links) {
                if (!link.matches("https?://\\S+")) throw new BadRequestException("Each link must start with http:// or https://");
            }
        }
        if (r.education() != null) d.education = cleanEducation(r.education());
        if (r.languages() != null) d.languages = IntakeAnswers.cleanList(r.languages(), 8, 40, "languages", "your career profile");
        if (r.visibility() != null) {
            Map<String, FieldVisibility> vis = new LinkedHashMap<>(storedVisibility(c));
            for (Map.Entry<String, String> e : r.visibility().entrySet()) {
                if (!DEFAULT_VISIBILITY.containsKey(e.getKey())) throw new BadRequestException("There's no career field called '" + e.getKey() + "'");
                FieldVisibility v = CareerEnums.parse(FieldVisibility.class, e.getValue(), "visibility");
                if (PAY_KEYS.contains(e.getKey()) && v != FieldVisibility.ONLY_ME) {
                    throw new BadRequestException("Your pay stays private. You can include it on an application when you apply.");
                }
                vis.put(e.getKey(), v);
            }
            Map<String, String> wire = new LinkedHashMap<>();
            vis.forEach((k, v) -> wire.put(k, CareerEnums.wire(v)));
            c.setVisibilityJson(writeJson(wire));
        }
    }

    private List<SkillEntry> cleanSkills(List<SkillEntry> raw) {
        Map<String, SkillEntry> out = new LinkedHashMap<>();
        for (SkillEntry s : raw) {
            if (s == null || s.name() == null || s.name().isBlank()) continue;
            String name = limit(s.name().trim(), 40, "Each skill");
            IntakeAnswers.cleanList(List.of(name), 1, 40, "skills", "your career profile");
            Proficiency p = s.proficiency() == null ? null : CareerEnums.parse(Proficiency.class, s.proficiency(), "proficiency");
            if (s.years() != null && (s.years() < 0 || s.years() > 50)) throw new BadRequestException("Skill years must be between 0 and 50");
            out.putIfAbsent(name.toLowerCase(Locale.ROOT), new SkillEntry(name, CareerEnums.wire(p), s.years()));
        }
        if (out.size() > MAX_SKILLS) throw new BadRequestException("You can add up to " + MAX_SKILLS + " skills");
        return List.copyOf(out.values());
    }

    private static Education cleanEducation(Education e) {
        String degree = e.degree() == null || e.degree().isBlank() ? null : CareerEnums.pick(CareerEnums.DEGREES, e.degree(), "degree");
        String institution = e.institution() == null || e.institution().isBlank() ? null : limit(e.institution().trim(), 100, "institution");
        if (e.year() != null && (e.year() < 1960 || e.year() > 2035)) throw new BadRequestException("year must be between 1960 and 2035");
        if (degree == null && institution == null && e.year() == null) return null;
        return new Education(degree, institution, e.year());
    }

    private static Integer pay(Integer v) {
        if (v != null && (v < 0 || v > MAX_COMPENSATION)) throw new BadRequestException("Compensation must be between 0 and " + MAX_COMPENSATION);
        return v;
    }

    private Map<String, FieldVisibility> visibilityOf(CareerProfile c) {
        Map<String, FieldVisibility> out = new LinkedHashMap<>(DEFAULT_VISIBILITY);
        out.putAll(storedVisibility(c));
        return out;
    }

    private Map<String, FieldVisibility> storedVisibility(CareerProfile c) {
        Map<String, FieldVisibility> out = new LinkedHashMap<>();
        try {
            Map<String, String> raw = objectMapper.readValue(c.getVisibilityJson() == null ? "{}" : c.getVisibilityJson(),
                    new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, String>>() { });
            raw.forEach((k, v) -> {
                if (DEFAULT_VISIBILITY.containsKey(k)) out.put(k, CareerEnums.parse(FieldVisibility.class, v, "visibility"));
            });
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Invalid stored career visibility", e);
        }
        return out;
    }

    private Stored readDetails(CareerProfile c) {
        try {
            return objectMapper.readValue(c.getDetailsJson() == null ? "{}" : c.getDetailsJson(), Stored.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Invalid stored career details", e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> List<T> emptyToNull(List<T> list) {
        return list == null || list.isEmpty() ? null : list;
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
