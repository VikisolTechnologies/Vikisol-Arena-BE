package com.vikisol.arena.profile.repository;

import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.entity.Industry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, UUID> {

    Optional<CandidateProfile> findByUserId(UUID userId);

    List<CandidateProfile> findByUserIdIn(List<UUID> userIds);

    // One IN-query for a whole list's profiles, keyed by user id - list endpoints use this instead
    // of one findByUserId() per row (the N+1 QueryCountTest guards against).
    default Map<UUID, CandidateProfile> mapByUserId(Collection<UUID> userIds) {
        if (userIds.isEmpty()) return Map.of();
        return findByUserIdIn(List.copyOf(new java.util.LinkedHashSet<>(userIds))).stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), Function.identity(), (a, b) -> a));
    }

    // `:text` is intentionally never null-checked here (always compared/used as a plain string,
    // caller passes "" for "no filter") - a `:text is null or ... :text ...` pattern in the same
    // query left Postgres/pgJDBC unable to infer a consistent parameter type for :text across its
    // two different usages (the null-check and the LIKE), producing "operator does not exist:
    // text ~~ bytea" at runtime regardless of the actual value passed in.
    // Deliberately NOT an @EntityGraph on skills/openTo here: those are @ElementCollection (i.e.
    // collection joins), and combining a collection-fetch @EntityGraph with Pageable makes
    // Hibernate paginate in memory (loads the full unbounded result set, then slices it in Java) -
    // worse than the per-row lazy loads this is meant to fix. See the IN-batched fetches below,
    // called once per page after this query returns, for the actual N+1 fix.
    // Row 17: people search candidates - live accounts not hidden from search, matching the term in
    // their name, title, skills or interests (skillsOnly: skills alone). Ranked in memory.
    @Query("""
            select distinct c from CandidateProfile c left join c.skills s left join c.interests i
            where c.user.deletedAt is null and c.user.id <> :viewerId and c.user.bannedAt is null
              and c.profileVisibility <> com.vikisol.arena.profile.entity.CandidateProfile.ProfileVisibility.HIDDEN
              and (lower(s.name) like concat('%', :term, '%')
                   or (:skillsOnly = false and (lower(c.name) like concat('%', :term, '%') or lower(c.title) like concat('%', :term, '%')
                        or lower(i) like concat('%', :term, '%'))))
            """)
    List<CandidateProfile> searchPeople(@Param("term") String term, @Param("skillsOnly") boolean skillsOnly,
                                        @Param("viewerId") UUID viewerId, Pageable pageable);

    // ARENA-APP-FLOW §8: talent search shows only people whose career profile is published
    // ("open"), on top of the search consent.
    // ARENA-FIX-EVERYTHING.md Phase 1 fix - this query never excluded an anonymized/erased
    // account (User.deletedAt set - see CandidateProfileService.deleteMyAccount). Without this,
    // exercising the DPDP right-to-erasure flow didn't actually remove a candidate from
    // enterprise search results, just renamed them to "Deleted user" and left them fully
    // visible and clickable - the opposite of what "erasure" is supposed to mean here.
    @Query("""
            select c from CandidateProfile c
            where c.consent.searchableByEnterprises = true
              and c.user.deletedAt is null
              and exists (select 1 from com.vikisol.arena.career.entity.CareerProfile cp
                          where cp.user = c.user and cp.publishedAt is not null)
              and (:industry is null or c.industry = :industry)
              and (:remoteOnly = false or c.remote = true)
              and (:text = '' or
                   lower(c.title) like concat('%', :text, '%') or
                   lower(c.location) like concat('%', :text, '%'))
            """)
    Page<CandidateProfile> search(@Param("text") String text, @Param("industry") Industry industry,
                                   @Param("remoteOnly") boolean remoteOnly, Pageable pageable);

    // Batched warm-up for the @ElementCollection fields the mapper/scoring code reads per row
    // (skills, openTo) - called once per page of search() results with all their ids, instead of
    // one lazy-load query per collection per candidate. Kept as two separate IN-queries (rather
    // than one @EntityGraph with both paths) to avoid the cartesian-product row blowup that
    // fetching two collections in a single query would cause.
    @EntityGraph(attributePaths = "skills")
    @Query("select c from CandidateProfile c where c.id in :ids")
    List<CandidateProfile> findByIdInFetchingSkills(@Param("ids") List<UUID> ids);

    @EntityGraph(attributePaths = "openTo")
    @Query("select c from CandidateProfile c where c.id in :ids")
    List<CandidateProfile> findByIdInFetchingOpenTo(@Param("ids") List<UUID> ids);

    List<CandidateProfile> findByIndustry(Industry industry);

    // Backs the public landing page's real stats (LandingService) - same
    // consent.searchableByEnterprises gate as search() above, so the count a logged-out visitor
    // sees always matches what an enterprise's Talent Universe search could actually reach.
    long countByConsent_SearchableByEnterprisesTrue();

    long countByConsent_SearchableByEnterprisesTrueAndDemoContentFalse();

    long countByIndustryAndConsent_SearchableByEnterprisesTrue(Industry industry);

    long countByIndustryAndConsent_SearchableByEnterprisesTrueAndDemoContentFalse(Industry industry);

    // DemoContentService - see PostRepository.findByDemoContentTrue()'s own comment.
    @EntityGraph(attributePaths = "user")
    List<CandidateProfile> findByDemoContentTrue();
}
