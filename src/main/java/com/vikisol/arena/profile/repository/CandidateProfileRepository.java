package com.vikisol.arena.profile.repository;

import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.entity.Industry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, UUID> {

    Optional<CandidateProfile> findByUserId(UUID userId);

    // `:text` is intentionally never null-checked here (always compared/used as a plain string,
    // caller passes "" for "no filter") - a `:text is null or ... :text ...` pattern in the same
    // query left Postgres/pgJDBC unable to infer a consistent parameter type for :text across its
    // two different usages (the null-check and the LIKE), producing "operator does not exist:
    // text ~~ bytea" at runtime regardless of the actual value passed in.
    @Query("""
            select c from CandidateProfile c
            where c.consent.searchableByEnterprises = true
              and (:industry is null or c.industry = :industry)
              and (:remoteOnly = false or c.remote = true)
              and (:text = '' or
                   lower(c.title) like concat('%', :text, '%') or
                   lower(c.location) like concat('%', :text, '%'))
            """)
    Page<CandidateProfile> search(@Param("text") String text, @Param("industry") Industry industry,
                                   @Param("remoteOnly") boolean remoteOnly, Pageable pageable);

    List<CandidateProfile> findByIndustry(Industry industry);
}
