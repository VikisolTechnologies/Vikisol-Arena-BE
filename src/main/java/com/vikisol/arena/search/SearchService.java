package com.vikisol.arena.search;

import com.vikisol.arena.company.dto.CompanyResponse;
import com.vikisol.arena.company.service.CompanyService;
import com.vikisol.arena.jobs.dto.JobResponse;
import com.vikisol.arena.jobs.service.JobService;
import com.vikisol.arena.marketplace.dto.ProjectResponse;
import com.vikisol.arena.marketplace.service.ProjectService;
import com.vikisol.arena.posts.entity.PostIntentType;
import com.vikisol.arena.posts.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * Arena-wide search: activities, discussions, open jobs, open projects and companies. Each source
 * reuses the same service call its own screen uses (so visibility, match scores and response
 * shapes are identical), then SearchText decides what matches and in what order.
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    public static final Set<String> TYPES = Set.of("all", "activities", "discussions", "jobs", "projects", "companies", "people", "skills");
    static final double DEFAULT_RADIUS_KM = 5;
    static final double MAX_RADIUS_KM = 50;
    // Upper bound on each source's candidate list - far above today's content, see SearchText.
    private static final int CANDIDATES = 1000;

    private final PostService postService;
    private final JobService jobService;
    private final ProjectService projectService;
    private final CompanyService companyService;
    private final com.vikisol.arena.profile.repository.CandidateProfileRepository candidateProfileRepository;
    private final com.vikisol.arena.follows.service.BlockService blockService;
    private final com.vikisol.arena.common.service.FileSigningService fileSigningService;

    @Transactional(readOnly = true)
    public SearchResponse search(String query, String type, int limit, UUID viewingUserId, Double nearLat, Double nearLng, Double radiusKm) {
        List<String> terms = SearchText.terms(query);
        String t = type == null || !TYPES.contains(type) ? "all" : type;
        boolean all = t.equals("all");
        if (terms.isEmpty()) {
            return new SearchResponse(query, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
        if (t.equals("people") || t.equals("skills")) {
            return new SearchResponse(query, List.of(), List.of(), List.of(), List.of(), List.of(),
                    viewingUserId == null ? List.of() : people(terms, t.equals("skills"), limit, viewingUserId, nearLat, nearLng, radiusKm));
        }
        return new SearchResponse(
                query,
                all || t.equals("activities")
                        ? postService.search(viewingUserId, terms, p -> p.getIntentType() == PostIntentType.ACTIVITY, limit) : List.of(),
                all || t.equals("discussions")
                        ? postService.search(viewingUserId, terms, p -> p.getIntentType().isDiscussion(), limit) : List.of(),
                all || t.equals("jobs") ? jobs(terms, limit, viewingUserId) : List.of(),
                all || t.equals("projects") ? projects(terms, limit, viewingUserId) : List.of(),
                all || t.equals("companies") ? companies(terms, limit, viewingUserId) : List.of(),
                List.of());
    }

    // Row 17 / 18: people (or skills) search. Hidden profiles never appear; "nearby" ones only in
    // a search near a point, within the radius; blocked people never appear either way.
    private List<SearchResponse.PersonResult> people(List<String> terms, boolean skillsOnly, int limit, UUID viewer,
                                                     Double lat, Double lng, Double radiusKm) {
        boolean near = lat != null && lng != null;
        if (near && (Math.abs(lat) > 90 || Math.abs(lng) > 180)) throw new com.vikisol.arena.common.exception.BadRequestException("near must be lat,lng");
        double radius = radiusKm == null ? DEFAULT_RADIUS_KM : Math.max(0.5, Math.min(radiusKm, MAX_RADIUS_KM));
        record Hit(com.vikisol.arena.profile.entity.CandidateProfile p, int score, Integer km) {
        }
        List<Hit> hits = new java.util.ArrayList<>();
        for (var p : candidateProfileRepository.searchPeople(terms.get(0), skillsOnly, viewer, PageRequest.of(0, CANDIDATES))) {
            Integer km = null;
            boolean located = p.getApproxLat() != null && p.getApproxLng() != null
                    && p.getLocationConsent() != com.vikisol.arena.profile.entity.LocationConsent.OFF;
            if (near) {
                if (!located) continue;
                double d = haversineKm(lat, lng, p.getApproxLat(), p.getApproxLng());
                if (d > radius) continue;
                km = (int) Math.max(1, Math.round(d));
            } else if (p.getProfileVisibility() != com.vikisol.arena.profile.entity.CandidateProfile.ProfileVisibility.EVERYONE) {
                continue;
            }
            List<String> skills = p.getSkills().stream().map(s -> s.getName()).toList();
            int score = skillsOnly ? SearchText.score(terms, null, SearchText.haystack(skills))
                    : SearchText.score(terms, p.getName(), SearchText.haystack(p.getTitle(), skills, p.getInterests()));
            if (score > 0) hits.add(new Hit(p, score, km));
        }
        return hits.stream()
                .sorted(Comparator.comparingInt((Hit h) -> h.score()).reversed()
                        .thenComparing(h -> h.km() == null ? Integer.MAX_VALUE : h.km()))
                .filter(h -> !blockService.isBlockedEitherDirection(viewer, h.p().getUser().getId()))
                .limit(limit)
                .map(h -> new SearchResponse.PersonResult(h.p().getUser().getId().toString(), h.p().getName(), h.p().getAvatarEmoji(),
                        fileSigningService.sign(h.p().getPhotoUrl()), h.p().getTitle(),
                        h.p().getSkills().stream().map(s -> s.getName()).limit(5).toList(),
                        h.p().getInterests().stream().limit(5).toList(), h.km()))
                .toList();
    }

    static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1), dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private List<JobResponse> jobs(List<String> terms, int limit, UUID viewer) {
        return rank(jobService.getOpenJobs(PageRequest.of(0, CANDIDATES), viewer).content(), limit,
                j -> SearchText.score(terms, j.title(), SearchText.haystack(
                        j.company(), j.industry(), j.location(), j.remote() ? "remote" : null, j.employmentType(), j.skills(), j.description())));
    }

    private List<ProjectResponse> projects(List<String> terms, int limit, UUID viewer) {
        return rank(projectService.getOpenProjects(PageRequest.of(0, CANDIDATES), viewer).content(), limit,
                p -> SearchText.score(terms, p.title(), SearchText.haystack(p.description(), p.skills(), p.postedBy())));
    }

    private List<CompanyResponse> companies(List<String> terms, int limit, UUID viewer) {
        return rank(companyService.listCompanies("", viewer, PageRequest.of(0, CANDIDATES)).content(), limit,
                c -> SearchText.score(terms, c.name(), SearchText.haystack(c.industry(), c.size())));
    }

    private static <T> List<T> rank(List<T> items, int limit, ToIntFunction<T> scorer) {
        record Hit<T>(T item, int score) {
        }
        return items.stream()
                .map(i -> new Hit<>(i, scorer.applyAsInt(i)))
                .filter(h -> h.score() > 0)
                .sorted(Comparator.comparingInt((Hit<T> h) -> h.score()).reversed())
                .limit(limit)
                .map(Hit::item)
                .toList();
    }
}
