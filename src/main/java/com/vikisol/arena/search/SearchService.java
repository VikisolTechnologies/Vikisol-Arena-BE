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

    public static final Set<String> TYPES = Set.of("all", "activities", "discussions", "jobs", "projects", "companies");
    // Upper bound on each source's candidate list - far above today's content, see SearchText.
    private static final int CANDIDATES = 1000;

    private final PostService postService;
    private final JobService jobService;
    private final ProjectService projectService;
    private final CompanyService companyService;

    @Transactional(readOnly = true)
    public SearchResponse search(String query, String type, int limit, UUID viewingUserId) {
        List<String> terms = SearchText.terms(query);
        String t = type == null || !TYPES.contains(type) ? "all" : type;
        boolean all = t.equals("all");
        if (terms.isEmpty()) {
            return new SearchResponse(query, List.of(), List.of(), List.of(), List.of(), List.of());
        }
        return new SearchResponse(
                query,
                all || t.equals("activities")
                        ? postService.search(viewingUserId, terms, p -> p.getIntentType() == PostIntentType.ACTIVITY, limit) : List.of(),
                all || t.equals("discussions")
                        ? postService.search(viewingUserId, terms, p -> p.getIntentType().isDiscussion(), limit) : List.of(),
                all || t.equals("jobs") ? jobs(terms, limit, viewingUserId) : List.of(),
                all || t.equals("projects") ? projects(terms, limit, viewingUserId) : List.of(),
                all || t.equals("companies") ? companies(terms, limit, viewingUserId) : List.of());
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
