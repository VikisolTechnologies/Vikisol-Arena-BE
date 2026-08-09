package com.vikisol.arena.notifications.service;

import com.vikisol.arena.applications.entity.Application;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.jobs.entity.JobPosting;
import com.vikisol.arena.notifications.entity.Notification;
import com.vikisol.arena.notifications.entity.NotificationType;
import com.vikisol.arena.notifications.repository.NotificationRepository;
import com.vikisol.arena.marketplace.entity.Bid;
import com.vikisol.arena.marketplace.entity.Project;
import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.entity.PostJoinRequest;
import com.vikisol.arena.profile.entity.CandidateProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central place every module drops a notification from, rather than each module writing directly
 * to NotificationRepository - keeps the "what triggers a notification" list discoverable in one
 * file instead of scattered across applications/interviews/marketplace services.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public Notification notify(User user, NotificationType type, String title, String body) {
        return notificationRepository.save(Notification.builder()
                .user(user).type(type).title(title).body(body).read(false).build());
    }

    public void notifyApplicationSubmitted(CandidateProfile candidate, JobPosting job) {
        notify(candidate.getUser(), NotificationType.AGENT, "Application submitted",
                "Your application to " + job.getTitle() + " at " + job.getEnterprise().getCompanyName() + " was submitted.");
        notify(job.getEnterprise().getUser(), NotificationType.SYSTEM, "New applicant",
                candidate.getName() + " applied to " + job.getTitle() + ".");
    }

    public void notifyStageChanged(Application application) {
        JobPosting job = application.getJobPosting();
        notify(application.getCandidate().getUser(), NotificationType.AGENT, "Application update",
                "Your application to " + job.getTitle() + " moved to " + application.getStage().wireValue() + ".");
    }

    public void notifyInterviewProposed(Application application) {
        notify(application.getCandidate().getUser(), NotificationType.INTERVIEW, "Interview slots proposed",
                application.getJobPosting().getEnterprise().getCompanyName() + " proposed interview slots for "
                        + application.getJobPosting().getTitle() + ".");
    }

    public void notifyInterviewConfirmed(Application application) {
        notify(application.getCandidate().getUser(), NotificationType.INTERVIEW, "Interview confirmed",
                "Your interview for " + application.getJobPosting().getTitle() + " is confirmed.");
        notify(application.getJobPosting().getEnterprise().getUser(), NotificationType.INTERVIEW, "Interview confirmed",
                application.getCandidate().getName() + " confirmed an interview slot for " + application.getJobPosting().getTitle() + ".");
    }

    public void notifyBidPlaced(Project project, Bid bid) {
        notify(project.getPostedByUser(), NotificationType.BID, "New bid received",
                bid.getBidderUser().getName() + " placed a bid of ₹" + bid.getAmount() + " on " + project.getTitle() + ".");
    }

    public void notifyBidAwarded(Bid bid) {
        notify(bid.getBidderUser(), NotificationType.BID, "Bid awarded",
                "Your bid on " + bid.getProject().getTitle() + " was awarded. The project is now underway.");
    }

    public void notifyMilestoneSubmitted(Project project, String milestoneLabel) {
        notify(project.getPostedByUser(), NotificationType.SYSTEM, "Deliverable submitted",
                "A deliverable was submitted for milestone \"" + milestoneLabel + "\" on " + project.getTitle() + ".");
    }

    public void notifyDeliverableReviewed(User deliverableOwner, String milestoneLabel, boolean accepted) {
        notify(deliverableOwner, NotificationType.SYSTEM, accepted ? "Deliverable accepted" : "Deliverable rejected",
                "Your deliverable for milestone \"" + milestoneLabel + "\" was " + (accepted ? "accepted" : "sent back for changes") + ".");
    }

    // ARENA-V2-PRODUCT-ARCHITECTURE.md Phase A (posts/rooms/follows) - all four reuse the
    // existing SYSTEM type rather than adding new NotificationType values, since the enum is
    // hand-mirrored on both frontend and backend and these events all fit "system" semantics.
    public void notifyPostJoinRequested(Post post, PostJoinRequest joinRequest) {
        notify(post.getAuthorUser(), NotificationType.SYSTEM, "New join request",
                joinRequest.getUser().getName() + " wants to join \"" + preview(post.getBody()) + "\".");
    }

    public void notifyPostJoinApproved(PostJoinRequest joinRequest) {
        notify(joinRequest.getUser(), NotificationType.SYSTEM, "Join request approved",
                "You're in! \"" + preview(joinRequest.getPost().getBody()) + "\" now has a room.");
    }

    public void notifyPostJoinDeclined(PostJoinRequest joinRequest) {
        notify(joinRequest.getUser(), NotificationType.SYSTEM, "Join request declined",
                "Your request to join \"" + preview(joinRequest.getPost().getBody()) + "\" wasn't accepted this time.");
    }

    public void notifyNewFollower(User following, User follower) {
        notify(following, NotificationType.SYSTEM, "New follower", follower.getName() + " started following you.");
    }

    public void notifyPostCancelled(User recipient, Post post) {
        notify(recipient, NotificationType.SYSTEM, "Activity cancelled", "\"" + preview(post.getBody()) + "\" was cancelled by its host.");
    }

    // §4 safety-audit fix: "creator can remove anyone" - the removed person needs to know why
    // they lost access, not just silently find the room gone.
    public void notifyRemovedFromRoom(User recipient, Post post) {
        notify(recipient, NotificationType.SYSTEM, "Removed from room", "You were removed from \"" + preview(post.getBody()) + "\" by its host.");
    }

    public void notifyActivityStartingSoon(User recipient, Post post) {
        notify(recipient, NotificationType.SYSTEM, "Starting soon", "\"" + preview(post.getBody()) + "\" starts within the hour.");
    }

    private String preview(String body) {
        return body.length() > 60 ? body.substring(0, 60) + "…" : body;
    }
}
