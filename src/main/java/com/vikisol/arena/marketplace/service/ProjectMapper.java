package com.vikisol.arena.marketplace.service;

import com.vikisol.arena.marketplace.dto.BidResponse;
import com.vikisol.arena.marketplace.dto.DeliverableResponse;
import com.vikisol.arena.marketplace.dto.MilestoneResponse;
import com.vikisol.arena.marketplace.dto.ProjectResponse;
import com.vikisol.arena.marketplace.entity.Bid;
import com.vikisol.arena.marketplace.entity.Milestone;
import com.vikisol.arena.marketplace.entity.MilestoneStatus;
import com.vikisol.arena.marketplace.entity.Project;
import com.vikisol.arena.marketplace.repository.DeliverableRepository;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProjectMapper {

    private final CandidateProfileRepository candidateProfileRepository;
    private final DeliverableRepository deliverableRepository;

    // Single-bid convenience overload (one extra query) - fine for one-off call sites like
    // placeBid's just-created bid. List/page call sites should use the batched overload below
    // with a pre-fetched bidderProfiles map instead, to avoid one query per bid.
    public BidResponse toResponse(Bid bid) {
        var profile = candidateProfileRepository.findByUserId(bid.getBidderUser().getId());
        return toResponse(bid, profile.orElse(null));
    }

    public BidResponse toResponse(Bid bid, Map<UUID, com.vikisol.arena.profile.entity.CandidateProfile> bidderProfiles) {
        return toResponse(bid, bidderProfiles.get(bid.getBidderUser().getId()));
    }

    private BidResponse toResponse(Bid bid, com.vikisol.arena.profile.entity.CandidateProfile profile) {
        String bidderName = bid.getBidderUser().getName();
        String bidderEmoji = "🧑🏽"; // generic person fallback
        if (profile != null) {
            bidderName = profile.getName();
            bidderEmoji = profile.getAvatarEmoji();
        }
        return new BidResponse(
                bid.getId().toString(), bid.getProject().getId().toString(), bidderName, bidderEmoji,
                bid.getAmount(), bid.getMatchPercentage(), bid.isAgentPick(), bid.getSubmittedAt().toString(),
                bid.getStatus().wireValue());
    }

    public MilestoneResponse toResponse(Milestone m) {
        DeliverableResponse deliverable = deliverableRepository.findByMilestoneIdOrderBySubmittedAtDesc(m.getId()).stream()
                .findFirst()
                .map(d -> new DeliverableResponse(d.getNote(), d.getSubmittedAt().toString()))
                .orElse(null);
        return new MilestoneResponse(m.getId().toString(), m.getLabel(), m.getAmount(), m.getStatus() == MilestoneStatus.ACCEPTED, m.getStatus().wireValue(), deliverable);
    }

    // One-off call sites (createProject with no bids yet) - no batching needed.
    public ProjectResponse toResponse(Project p, List<Bid> bids, List<Milestone> milestones, UUID viewingUserId) {
        return toResponse(p, bids, milestones, viewingUserId, Map.of());
    }

    public ProjectResponse toResponse(Project p, List<Bid> bids, List<Milestone> milestones, UUID viewingUserId,
                                       Map<UUID, com.vikisol.arena.profile.entity.CandidateProfile> bidderProfiles) {
        boolean mine = viewingUserId != null && p.getPostedByUser().getId().equals(viewingUserId);
        return new ProjectResponse(
                p.getId().toString(), p.getTitle(), p.getDescription(), p.getBudgetMin(), p.getBudgetMax(),
                p.getDurationWeeks(), p.getSkills(), p.getPostedByUser().getName(), p.getStatus().wireValue(),
                p.getEndsAt().toString(),
                bids.stream().map(b -> toResponse(b, bidderProfiles)).toList(),
                mine ? Boolean.TRUE : null,
                p.getAwardedBidId() == null ? null : p.getAwardedBidId().toString(),
                milestones.isEmpty() ? List.of() : milestones.stream().map(this::toResponse).toList()
        );
    }
}
