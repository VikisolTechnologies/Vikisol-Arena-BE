package com.vikisol.arena.landing.service;

import com.vikisol.arena.landing.dto.IndustryStat;
import com.vikisol.arena.landing.dto.LandingStatsResponse;
import com.vikisol.arena.marketplace.dto.ProjectResponse;
import com.vikisol.arena.marketplace.entity.Bid;
import com.vikisol.arena.marketplace.entity.Project;
import com.vikisol.arena.marketplace.entity.ProjectStatus;
import com.vikisol.arena.marketplace.repository.BidRepository;
import com.vikisol.arena.marketplace.repository.ProjectRepository;
import com.vikisol.arena.marketplace.service.ProjectMapper;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.entity.Industry;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Backs the public, logged-out landing page (arena.vikisol.in) - every stat and the featured
 *  "live bid" card shown there used to be hardcoded marketing copy that never changed and never
 *  matched reality. Every value returned here is a real, current query result. */
@Service
@RequiredArgsConstructor
public class LandingService {

    // How many of the most-recently-posted OPEN projects to scan for the one with the most bids -
    // small and fixed since this only feeds a single marketing-page card, not a real browse/search
    // surface (that's the authenticated /marketplace/projects).
    private static final int FEATURED_CANDIDATE_POOL = 10;
    private static final int FEATURED_TOP_BIDS = 3;

    private final CandidateProfileRepository candidateProfileRepository;
    private final ProjectRepository projectRepository;
    private final BidRepository bidRepository;
    private final ProjectMapper mapper;

    @Transactional(readOnly = true)
    public LandingStatsResponse getStats() {
        long openToWork = candidateProfileRepository.countByConsent_SearchableByEnterprisesTrue();
        List<IndustryStat> byIndustry = Arrays.stream(Industry.values())
                .map(i -> new IndustryStat(i.wireValue(),
                        candidateProfileRepository.countByIndustryAndConsent_SearchableByEnterprisesTrue(i)))
                .filter(stat -> stat.count() > 0)
                .toList();
        long openProjects = projectRepository.countByStatus(ProjectStatus.OPEN);
        return new LandingStatsResponse(openToWork, byIndustry, openProjects);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getFeaturedProject() {
        var pool = projectRepository.findByStatus(ProjectStatus.OPEN,
                PageRequest.of(0, FEATURED_CANDIDATE_POOL, Sort.by(Sort.Direction.DESC, "createdAt")));
        if (pool.isEmpty()) return null;

        Project best = null;
        List<Bid> bestBids = List.of();
        for (Project candidate : pool.getContent()) {
            List<Bid> bids = bidRepository.findByProjectIdOrderByAmountDesc(candidate.getId());
            if (best == null || bids.size() > bestBids.size()) {
                best = candidate;
                bestBids = bids;
            }
        }

        List<Bid> topBids = bestBids.size() > FEATURED_TOP_BIDS ? bestBids.subList(0, FEATURED_TOP_BIDS) : bestBids;
        Map<UUID, CandidateProfile> bidderProfiles = topBids.isEmpty() ? Map.of()
                : candidateProfileRepository.findByUserIdIn(
                                topBids.stream().map(b -> b.getBidderUser().getId()).distinct().toList())
                        .stream().collect(Collectors.toMap(c -> c.getUser().getId(), c -> c));

        // viewingUserId=null - a logged-out visitor, so `mine` is always false regardless of who
        // actually posted it (matches ProjectMapper's existing null-viewer contract).
        return mapper.toResponse(best, topBids, List.of(), null, bidderProfiles);
    }
}
