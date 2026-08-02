package com.vikisol.arena.marketplace.service;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.marketplace.dto.*;
import com.vikisol.arena.marketplace.entity.*;
import com.vikisol.arena.marketplace.repository.BidRepository;
import com.vikisol.arena.marketplace.repository.DeliverableRepository;
import com.vikisol.arena.marketplace.repository.MilestoneRepository;
import com.vikisol.arena.marketplace.repository.ProjectRepository;
import com.vikisol.arena.marketplace.repository.RatingRepository;
import com.vikisol.arena.matching.ScoringService;
import com.vikisol.arena.notifications.service.NotificationService;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private static final List<String> DEFAULT_MILESTONE_LABELS =
            List.of("Kickoff call scheduled", "First milestone delivered", "Final review", "Project complete");

    private final ProjectRepository projectRepository;
    private final BidRepository bidRepository;
    private final MilestoneRepository milestoneRepository;
    private final DeliverableRepository deliverableRepository;
    private final RatingRepository ratingRepository;
    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final ProjectMapper mapper;
    private final ScoringService scoringService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public PagedResponse<ProjectResponse> getOpenProjects(Pageable pageable, UUID viewingUserId) {
        return PagedResponse.of(projectRepository.findByStatus(ProjectStatus.OPEN, pageable),
                p -> mapper.toResponse(p, bidRepository.findByProjectIdOrderByAmountDesc(p.getId()), List.of(), viewingUserId));
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(UUID id, UUID viewingUserId) {
        Project project = requireProject(id);
        return mapper.toResponse(project, bidRepository.findByProjectIdOrderByAmountDesc(id),
                milestoneRepository.findByProjectIdOrderByOrderIndexAsc(id), viewingUserId);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ProjectResponse> getMyProjects(UUID userId, Pageable pageable) {
        return PagedResponse.of(projectRepository.findByPostedByUserId(userId, pageable),
                p -> mapper.toResponse(p, bidRepository.findByProjectIdOrderByAmountDesc(p.getId()),
                        milestoneRepository.findByProjectIdOrderByOrderIndexAsc(p.getId()), userId));
    }

    @Transactional(readOnly = true)
    public PagedResponse<BidResponse> getMyBids(UUID userId, Pageable pageable) {
        return PagedResponse.of(bidRepository.findByBidderUserIdOrderBySubmittedAtDesc(userId, pageable), mapper::toResponse);
    }

    @Transactional
    public ProjectResponse createProject(UUID userId, CreateProjectRequest request) {
        User poster = requireUser(userId);
        Project project = Project.builder()
                .postedByUser(poster)
                .title(request.title())
                .description(request.description())
                .budgetMin(request.budgetMin())
                .budgetMax(request.budgetMax())
                .durationWeeks(request.durationWeeks())
                .skills(request.skills())
                .status(ProjectStatus.OPEN)
                .endsAt(Instant.now().plus(Duration.ofDays(5)))
                .build();
        project = projectRepository.save(project);
        return mapper.toResponse(project, List.of(), List.of(), userId);
    }

    @Transactional
    public BidResponse placeBid(UUID userId, UUID projectId, int amount) {
        Project project = requireProject(projectId);
        if (project.getStatus() != ProjectStatus.OPEN) {
            throw new BadRequestException("This project is no longer accepting bids");
        }
        User bidder = requireUser(userId);

        int matchPercentage = 75;
        CandidateProfile candidate = candidateProfileRepository.findByUserId(userId).orElse(null);
        if (candidate != null) {
            matchPercentage = scoringService.computeMatchPercentage(candidate, new HashSet<>(project.getSkills()), null);
        }

        Bid bid = Bid.builder()
                .project(project).bidderUser(bidder).amount(amount).matchPercentage(matchPercentage)
                .agentPick(false).status(BidStatus.PENDING).submittedAt(Instant.now())
                .build();
        bid = bidRepository.save(bid);

        notificationService.notifyBidPlaced(project, bid);
        return mapper.toResponse(bid);
    }

    @Transactional
    public ProjectResponse award(UUID userId, UUID projectId, UUID bidId) {
        Project project = requireProject(projectId);
        if (!project.getPostedByUser().getId().equals(userId)) {
            throw new AccessDeniedException("Not your project");
        }
        List<Bid> bids = bidRepository.findByProjectIdOrderByAmountDesc(projectId);
        Bid awarded = bids.stream().filter(b -> b.getId().equals(bidId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Bid not found: " + bidId));

        for (Bid b : bids) {
            b.setStatus(b.getId().equals(bidId) ? BidStatus.WON : BidStatus.LOST);
        }
        bidRepository.saveAll(bids);

        project.setStatus(ProjectStatus.AWARDED);
        project.setAwardedBidId(bidId);
        projectRepository.save(project);

        List<Milestone> milestones = new java.util.ArrayList<>();
        for (int i = 0; i < DEFAULT_MILESTONE_LABELS.size(); i++) {
            milestones.add(Milestone.builder().project(project).label(DEFAULT_MILESTONE_LABELS.get(i))
                    .orderIndex(i).status(MilestoneStatus.PENDING).build());
        }
        milestoneRepository.saveAll(milestones);

        notificationService.notifyBidAwarded(awarded);
        return mapper.toResponse(project, bids, milestones, userId);
    }

    @Transactional
    public MilestoneResponse submitDeliverable(UUID userId, UUID milestoneId, SubmitDeliverableRequest request) {
        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found: " + milestoneId));
        Project project = milestone.getProject();
        assertAwardedBidder(userId, project);

        Deliverable deliverable = Deliverable.builder()
                .milestone(milestone).submittedByUser(requireUser(userId))
                .note(request.note()).fileUrl(request.fileUrl())
                .status(DeliverableStatus.SUBMITTED).submittedAt(Instant.now())
                .build();
        deliverableRepository.save(deliverable);

        milestone.setStatus(MilestoneStatus.SUBMITTED);
        milestone = milestoneRepository.save(milestone);

        notificationService.notifyMilestoneSubmitted(project, milestone.getLabel());
        return mapper.toResponse(milestone);
    }

    @Transactional
    public MilestoneResponse reviewMilestone(UUID userId, UUID milestoneId, boolean accept) {
        Milestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found: " + milestoneId));
        Project project = milestone.getProject();
        if (!project.getPostedByUser().getId().equals(userId)) {
            throw new AccessDeniedException("Not your project");
        }
        if (milestone.getStatus() != MilestoneStatus.SUBMITTED) {
            throw new BadRequestException("This milestone has no pending submission to review");
        }
        milestone.setStatus(accept ? MilestoneStatus.ACCEPTED : MilestoneStatus.REJECTED);
        milestone = milestoneRepository.save(milestone);

        List<Milestone> all = milestoneRepository.findByProjectIdOrderByOrderIndexAsc(project.getId());
        if (all.stream().allMatch(m -> m.getStatus() == MilestoneStatus.ACCEPTED)) {
            project.setStatus(ProjectStatus.CLOSED);
            projectRepository.save(project);
        }

        User deliverableOwner = deliverableRepository.findByMilestoneIdOrderBySubmittedAtDesc(milestoneId).stream()
                .findFirst().map(Deliverable::getSubmittedByUser).orElse(null);
        if (deliverableOwner != null) {
            notificationService.notifyDeliverableReviewed(deliverableOwner, milestone.getLabel(), accept);
        }
        return mapper.toResponse(milestone);
    }

    @Transactional
    public void rate(UUID userId, UUID projectId, RateRequest request) {
        Project project = requireProject(projectId);
        if (project.getStatus() != ProjectStatus.CLOSED) {
            throw new BadRequestException("This project isn't complete yet");
        }
        Bid awardedBid = project.getAwardedBidId() == null ? null
                : bidRepository.findById(project.getAwardedBidId()).orElse(null);
        if (awardedBid == null) {
            throw new BadRequestException("No awarded bidder to rate");
        }

        boolean isClient = project.getPostedByUser().getId().equals(userId);
        boolean isTalent = awardedBid.getBidderUser().getId().equals(userId);
        if (!isClient && !isTalent) {
            throw new AccessDeniedException("Only the client or the awarded talent can rate this project");
        }

        User toUser = isClient ? awardedBid.getBidderUser() : project.getPostedByUser();
        RatingDirection direction = isClient ? RatingDirection.CLIENT_TO_TALENT : RatingDirection.TALENT_TO_CLIENT;

        ratingRepository.save(Rating.builder()
                .project(project).fromUser(requireUser(userId)).toUser(toUser)
                .direction(direction).score(request.score()).comment(request.comment())
                .build());
    }

    private void assertAwardedBidder(UUID userId, Project project) {
        if (project.getAwardedBidId() == null) {
            throw new BadRequestException("This project hasn't been awarded yet");
        }
        Bid awarded = bidRepository.findById(project.getAwardedBidId())
                .orElseThrow(() -> new ResourceNotFoundException("Awarded bid not found"));
        if (!awarded.getBidderUser().getId().equals(userId)) {
            throw new AccessDeniedException("Only the awarded talent can submit a deliverable");
        }
    }

    private Project requireProject(UUID id) {
        return projectRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }
}
