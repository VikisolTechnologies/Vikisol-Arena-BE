package com.vikisol.arena.profile.repository;

import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.entity.Industry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, UUID> {

    Optional<CandidateProfile> findByUserId(UUID userId);

    List<CandidateProfile> findByUserIdIn(List<UUID> userIds);

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
}
