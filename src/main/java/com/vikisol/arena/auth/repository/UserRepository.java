package com.vikisol.arena.auth.repository;

import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByHandle(String handle);
    Optional<User> findByHandle(String handle);
    long countByCreatedAtAfter(Instant since);

    // V9 phone/Google sign-in - phoneNumber/googleId are unique-when-present (see
    // V9__auth_expansion.sql), so at most one row can ever match.
    Optional<User> findByPhoneNumber(String phoneNumber);
    Optional<User> findByGoogleId(String googleId);

    // PA5 (platform analytics): user-count-by-role breakdown as a SQL GROUP BY instead of
    // PlatformAnalyticsService loading every user row into memory and counting in Java.
    @Query("select u.role as role, count(u) as count from User u group by u.role")
    List<RoleCount> countByRoleGrouped();

    // PA3 (global user search). `q` is always a non-null, possibly-empty string from the
    // service layer (see EnterpriseProfileRepository.search()'s identical comment); `role`
    // stays genuinely nullable since an enum equality check doesn't hit the same JDBC
    // type-inference ambiguity a string LIKE does.
    @Query("""
            select u from User u where (:q = '' or lower(u.name) like lower(concat('%', :q, '%'))
            or lower(u.email) like lower(concat('%', :q, '%'))) and (:role is null or u.role = :role)
            order by u.createdAt desc
            """)
    Page<User> search(@Param("q") String q, @Param("role") Role role, Pageable pageable);

    interface RoleCount {
        Role getRole();
        long getCount();
    }
}
