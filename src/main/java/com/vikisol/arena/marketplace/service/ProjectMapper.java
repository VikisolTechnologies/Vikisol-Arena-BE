package com.vikisol.arena.marketplace.service;

import com.vikisol.arena.marketplace.dto.BidResponse;
import com.vikisol.arena.marketplace.dto.MilestoneResponse;
import com.vikisol.arena.marketplace.dto.ProjectResponse;
import com.vikisol.arena.marketplace.entity.Bid;
import com.vikisol.arena.marketplace.entity.Milestone;
import com.vikisol.arena.marketplace.entity.MilestoneStatus;
import com.vikisol.arena.marketplace.entity.Project;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProjectMapper {

    private final CandidateProfileRepository candidateProfileRepository;

    public BidResponse toResponse(Bid bid) {
        String bidderName = bid.getBidderUser().getName();
        String bidderEmoji = "🧑🏽"; // generic person fallback
        var profile = candidateProfileRepository.findByUserId(bid.getBidderUser().getId());
        if (profile.isPresent()) {
            bidderName = profile.get().getName();
            bidderEmoji = profile.get().getAvatarEmoji();
        }
        return new BidResponse(
                bid.getId().toString(), bid.getProject().getId().toString(), bidderName, bidderEmoji,
                bid.getAmount(), bid.getMatchPercentage(), bid.isAgentPick(), bid.getSubmittedAt().toString(),
                bid.getStatus().wireValue());
    }

    public MilestoneResponse toResponse(Milestone m) {
        return new MilestoneResponse(m.getId().toString(), m.getLabel(), m.getStatus() == MilestoneStatus.ACCEPTED, m.getStatus().wireValue());
    }

    public ProjectResponse toResponse(Project p, List<Bid> bids, List<Milestone> milestones, UUID viewingUserId) {
        boolean mine = viewingUserId != null && p.getPostedByUser().getId().equals(viewingUserId);
        return new ProjectResponse(
                p.getId().toString(), p.getTitle(), p.getDescription(), p.getBudgetMin(), p.getBudgetMax(),
                p.getDurationWeeks(), p.getSkills(), p.getPostedByUser().getName(), p.getStatus().wireValue(),
                p.getEndsAt().toString(),
                bids.stream().map(this::toResponse).toList(),
                mine ? Boolean.TRUE : null,
                p.getAwardedBidId() == null ? null : p.getAwardedBidId().toString(),
                milestones.isEmpty() ? List.of() : milestones.stream().map(this::toResponse).toList()
        );
    }
}
