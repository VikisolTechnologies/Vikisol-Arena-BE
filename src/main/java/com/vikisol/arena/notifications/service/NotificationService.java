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
    private final com.vikisol.arena.notifications.repository.NotificationPreferenceRepository preferenceRepository;

    // Row 16 categories. SAFETY is always delivered.
    public static final String ACTIVITY = "activity";
    public static final String NEED = "need";
    public static final String JOB = "job";
    public static final String MESSAGE = "message";
    public static final String SAFETY = "safety";

    // Always stored (seeds rely on the returned row); the category follows the type.
    @Transactional
    public Notification notify(User user, NotificationType type, String title, String body) {
        return notificationRepository.save(Notification.builder()
                .user(user).type(type).category(defaultCategory(type)).title(title).body(body).read(false).build());
    }

    // Row 18: respects the person's preferences for that category. Returns null when they turned
    // it off (nothing is stored).
    @Transactional
    public Notification notify(User user, NotificationType type, String category, String title, String body) {
        if (!wants(user, category)) return null;
        return notificationRepository.save(Notification.builder()
                .user(user).type(type).category(category).title(title).body(body).read(false).build());
    }

    public boolean wants(User user, String category) {
        if (category == null || SAFETY.equals(category) || user == null || user.getId() == null) return true;
        return preferenceRepository.findByUserId(user.getId()).map(p -> switch (category) {
            case ACTIVITY -> p.isActivity();
            case NEED -> p.isNeed();
            case JOB -> p.isJob();
            case MESSAGE -> p.isMessage();
            default -> true;
        }).orElse(true);
    }

    private static String defaultCategory(NotificationType type) {
        return switch (type) {
            case AGENT, INTERVIEW, BID -> JOB;
            case SYSTEM -> null;
        };
    }

    public void notifyNewMessage(User recipient, String body) {
        notify(recipient, NotificationType.SYSTEM, MESSAGE, "New message", body);
    }

    public void notifyApplicationSubmitted(CandidateProfile candidate, JobPosting job) {
        notify(candidate.getUser(), NotificationType.AGENT, JOB, "Application submitted",
                "Your application to " + job.getTitle() + " at " + job.getEnterprise().getCompanyName() + " was submitted.");
        notify(job.getEnterprise().getUser(), NotificationType.SYSTEM, JOB, "New applicant",
                candidate.getName() + " applied to " + job.getTitle() + ".");
    }

    public void notifyStageChanged(Application application) {
        JobPosting job = application.getJobPosting();
        notify(application.getCandidate().getUser(), NotificationType.AGENT, JOB, "Application update",
                "Your application to " + job.getTitle() + " moved to " + application.getStage().wireValue() + ".");
    }

    public void notifyInterviewProposed(Application application) {
        notify(application.getCandidate().getUser(), NotificationType.INTERVIEW, JOB, "Interview slots proposed",
                application.getJobPosting().getEnterprise().getCompanyName() + " proposed interview slots for "
                        + application.getJobPosting().getTitle() + ".");
    }

    public void notifyInterviewConfirmed(Application application) {
        notify(application.getCandidate().getUser(), NotificationType.INTERVIEW, JOB, "Interview confirmed",
                "Your interview for " + application.getJobPosting().getTitle() + " is confirmed.");
        notify(application.getJobPosting().getEnterprise().getUser(), NotificationType.INTERVIEW, JOB, "Interview confirmed",
                application.getCandidate().getName() + " confirmed an interview slot for " + application.getJobPosting().getTitle() + ".");
    }

    public void notifyBidPlaced(Project project, Bid bid) {
        notify(project.getPostedByUser(), NotificationType.BID, JOB, "New bid received",
                bid.getBidderUser().getName() + " placed a bid of ₹" + bid.getAmount() + " on " + project.getTitle() + ".");
    }

    public void notifyBidAwarded(Bid bid) {
        notify(bid.getBidderUser(), NotificationType.BID, JOB, "Bid awarded",
                "Your bid on " + bid.getProject().getTitle() + " was awarded. The project is now underway.");
    }

    public void notifyMilestoneSubmitted(Project project, String milestoneLabel) {
        notify(project.getPostedByUser(), NotificationType.SYSTEM, JOB, "Deliverable submitted",
                "A deliverable was submitted for milestone \"" + milestoneLabel + "\" on " + project.getTitle() + ".");
    }

    public void notifyDeliverableReviewed(User deliverableOwner, String milestoneLabel, boolean accepted) {
        notify(deliverableOwner, NotificationType.SYSTEM, JOB, accepted ? "Deliverable accepted" : "Deliverable rejected",
                "Your deliverable for milestone \"" + milestoneLabel + "\" was " + (accepted ? "accepted" : "sent back for changes") + ".");
    }

    // ARENA-V2-PRODUCT-ARCHITECTURE.md Phase A (posts/rooms/follows) - all four reuse the
    // existing SYSTEM type rather than adding new NotificationType values, since the enum is
    // hand-mirrored on both frontend and backend and these events all fit "system" semantics.
    public void notifyPostJoinRequested(Post post, PostJoinRequest joinRequest) {
        notify(post.getAuthorUser(), NotificationType.SYSTEM, ACTIVITY, "New join request",
                joinRequest.getUser().getName() + " wants to join \"" + preview(post.getBody()) + "\".");
    }

    public void notifyPostJoinApproved(PostJoinRequest joinRequest) {
        notify(joinRequest.getUser(), NotificationType.SYSTEM, ACTIVITY, "Join request approved",
                "You're in! \"" + preview(joinRequest.getPost().getBody()) + "\" now has a room.");
    }

    public void notifyPostJoinWithdrawn(Post post, String participantName, boolean hadJoined) {
        notify(post.getAuthorUser(), NotificationType.SYSTEM, ACTIVITY,
                hadJoined ? "Someone left" : "Join request withdrawn",
                participantName + (hadJoined ? " left \"" : " withdrew their request to join \"") + preview(post.getBody()) + "\".");
    }

    public void notifyJoinOutcome(PostJoinRequest joinRequest) {
        boolean showed = joinRequest.getOutcome() == com.vikisol.arena.posts.entity.PostJoinOutcome.ATTENDED;
        notify(joinRequest.getUser(), NotificationType.SYSTEM, ACTIVITY,
                showed ? "You were marked present" : "You were marked as a no-show",
                "The host recorded that you " + (showed ? "showed up for \"" : "didn't show up for \"")
                        + preview(joinRequest.getPost().getBody()) + "\".");
    }

    public void notifyPostJoinDeclined(PostJoinRequest joinRequest) {
        notify(joinRequest.getUser(), NotificationType.SYSTEM, ACTIVITY, "Join request declined",
                "Your request to join \"" + preview(joinRequest.getPost().getBody()) + "\" wasn't accepted this time.");
    }

    public void notifyNewFollower(User following, User follower) {
        notify(following, NotificationType.SYSTEM, "New follower", follower.getName() + " started following you.");
    }

    public void notifyPostCancelled(User recipient, Post post) {
        notify(recipient, NotificationType.SYSTEM, ACTIVITY, "Activity cancelled", "\"" + preview(post.getBody()) + "\" was cancelled by its host."
                + (post.getCancelReason() == null ? "" : " Their reason: " + post.getCancelReason()));
    }

    // §4 safety-audit fix: "creator can remove anyone" - the removed person needs to know why
    // they lost access, not just silently find the room gone.
    public void notifyRemovedFromRoom(User recipient, Post post) {
        notify(recipient, NotificationType.SYSTEM, SAFETY, "Removed from room", "You were removed from \"" + preview(post.getBody()) + "\" by its host.");
    }

    public void notifyActivityStartingSoon(User recipient, Post post) {
        notify(recipient, NotificationType.SYSTEM, ACTIVITY, "Starting soon", "\"" + preview(post.getBody()) + "\" starts within the hour.");
    }

    // New features (activities, needs, jobs) use the existing SYSTEM type, same reasoning as the
    // Phase A notifications above.
    public void notifySystem(User recipient, String title, String body) {
        notify(recipient, NotificationType.SYSTEM, title, body);
    }

    // Row 16/18: with a category, so it can be filtered and turned off.
    public void notifySystem(User recipient, String category, String title, String body) {
        notify(recipient, NotificationType.SYSTEM, category, title, body);
    }

    public void notifyActivity(User recipient, String title, String body) {
        notifySystem(recipient, ACTIVITY, title, body);
    }

    public void notifyNeed(User recipient, String title, String body) {
        notifySystem(recipient, NEED, title, body);
    }

    public void notifyJob(User recipient, String title, String body) {
        notifySystem(recipient, JOB, title, body);
    }

    private String preview(String body) {
        return body.length() > 60 ? body.substring(0, 60) + "…" : body;
    }
}
