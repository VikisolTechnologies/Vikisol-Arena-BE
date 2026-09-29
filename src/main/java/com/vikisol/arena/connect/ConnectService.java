package com.vikisol.arena.connect;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.business.repository.BusinessVerificationRepository;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.common.policy.ProtectedAttributes;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.service.EnterpriseProfileService;
import com.vikisol.arena.follows.service.BlockService;
import com.vikisol.arena.jobs.entity.JobPosting;
import com.vikisol.arena.jobs.repository.JobPostingRepository;
import com.vikisol.arena.messaging.service.ConversationService;
import com.vikisol.arena.notifications.service.NotificationService;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

// Flow §8 / FE-API-GAPS row 34: reaching out from talent search sends a connect request the person
// can accept or decline. Messaging a person who hasn't applied opens only after they accept.
@Service
@RequiredArgsConstructor
public class ConnectService {

    private final ConnectRequestRepository repository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final EnterpriseProfileService enterpriseProfileService;
    private final JobPostingRepository jobPostingRepository;
    private final UserRepository userRepository;
    private final BlockService blockService;
    private final NotificationService notificationService;
    private final ConversationService conversationService;
    private final BusinessVerificationRepository verificationRepository;

    public record ConnectView(String id, String companyId, String companyName, String companyEmoji, boolean companyVerified,
                              String jobId, String jobTitle, String note, String status, String createdAt, String conversationId) {
    }

    // Employer side. {candidateId} is the talent-search id (CandidateProfile id).
    @Transactional
    public ConnectView send(UUID senderId, UUID candidateId, UUID jobId, String note) {
        EnterpriseProfile tenant = enterpriseProfileService.getEntityForUser(senderId);
        CandidateProfile candidate = candidateProfileRepository.findById(candidateId)
                .filter(c -> c.getUser().getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found: " + candidateId));
        UUID candidateUserId = candidate.getUser().getId();
        if (blockService.isBlockedEitherDirection(senderId, candidateUserId)) throw new BadRequestException("You can't contact this person");
        ProtectedAttributes.reject("the note", note);
        JobPosting job = null;
        if (jobId != null) {
            job = jobPostingRepository.findById(jobId).filter(j -> j.getEnterprise().getId().equals(tenant.getId()))
                    .orElseThrow(() -> new BadRequestException("That job isn't one of your company's"));
        }
        ConnectRequest existing = repository.findByTenantIdAndCandidateId(tenant.getId(), candidateUserId).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == ConnectRequest.Status.DECLINED) throw new BadRequestException("They declined your company's request");
            return view(existing, null);
        }
        ConnectRequest request = repository.save(ConnectRequest.builder().tenant(tenant).candidate(candidate.getUser())
                .sender(userRepository.getReferenceById(senderId)).job(job).note(note.trim()).build());
        notificationService.notifyJob(candidate.getUser(), "A company would like to connect",
                tenant.getCompanyName() + (job != null ? " (about " + job.getTitle() + ")" : "") + " would like to talk. You decide.");
        return view(request, null);
    }

    // Person side: every request, newest first.
    @Transactional(readOnly = true)
    public Page<ConnectView> mine(UUID userId, Pageable pageable) {
        return repository.findByCandidateIdOrderByCreatedAtDescIdDesc(userId, pageable).map(r -> view(r, null));
    }

    @Transactional
    public ConnectView decide(UUID userId, UUID requestId, boolean accept) {
        ConnectRequest request = repository.findById(requestId)
                .filter(r -> r.getCandidate().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Request not found: " + requestId));
        if (request.getStatus() != ConnectRequest.Status.PENDING) throw new BadRequestException("You've already answered this request");
        request.setStatus(accept ? ConnectRequest.Status.ACCEPTED : ConnectRequest.Status.DECLINED);
        request.setDecidedAt(Instant.now());
        repository.save(request);
        String conversationId = null;
        if (accept && request.getSender() != null) {
            conversationId = conversationService.getOrCreate(userId, request.getSender().getId(),
                    "About: " + (request.getJob() != null ? request.getJob().getTitle() : request.getTenant().getCompanyName())).id();
            notificationService.notifyJob(request.getSender(), "Connect request accepted",
                    userRepository.getReferenceById(userId).getName() + " accepted. You can message them now.");
        }
        return view(request, conversationId);
    }

    private ConnectView view(ConnectRequest r, String conversationId) {
        EnterpriseProfile t = r.getTenant();
        boolean verified = verificationRepository.findByTenantId(t.getId())
                .map(v -> v.getStatus() == com.vikisol.arena.business.entity.BusinessVerification.Status.VERIFIED).orElse(false);
        return new ConnectView(r.getId().toString(), t.getId().toString(), t.getCompanyName(), t.getLogoEmoji(), verified,
                r.getJob() == null ? null : r.getJob().getId().toString(), r.getJob() == null ? null : r.getJob().getTitle(),
                r.getNote(), r.getStatus().name().toLowerCase(), r.getCreatedAt().toString(), conversationId);
    }
}
