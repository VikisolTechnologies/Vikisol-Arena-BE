package com.vikisol.arena.platform.repository;

import com.vikisol.arena.platform.entity.FeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {
    List<FeatureFlag> findAllByOrderByKeyAsc();
    Optional<FeatureFlag> findByKey(String key);
}
