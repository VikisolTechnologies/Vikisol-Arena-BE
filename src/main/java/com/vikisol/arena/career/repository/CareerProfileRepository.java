package com.vikisol.arena.career.repository;

import com.vikisol.arena.career.entity.CareerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface CareerProfileRepository extends JpaRepository<CareerProfile, UUID> {

    Optional<CareerProfile> findByUserId(UUID userId);

    List<CareerProfile> findByUserIdIn(Collection<UUID> userIds);

    default Map<UUID, CareerProfile> mapByUserId(Collection<UUID> userIds) {
        if (userIds.isEmpty()) return Map.of();
        return findByUserIdIn(userIds).stream().collect(Collectors.toMap(c -> c.getUser().getId(), Function.identity()));
    }
}
