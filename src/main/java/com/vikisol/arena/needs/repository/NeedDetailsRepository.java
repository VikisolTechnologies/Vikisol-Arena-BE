package com.vikisol.arena.needs.repository;

import com.vikisol.arena.needs.entity.NeedDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NeedDetailsRepository extends JpaRepository<NeedDetails, UUID> {
    Optional<NeedDetails> findByPostId(UUID postId);

    List<NeedDetails> findByPostIdIn(Collection<UUID> postIds);
}
