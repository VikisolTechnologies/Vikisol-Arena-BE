package com.vikisol.arena.jobs.service;

import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.jobs.dto.JobResponse;
import com.vikisol.arena.jobs.entity.JobPosting;
import com.vikisol.arena.jobs.entity.PostingStatus;
import com.vikisol.arena.jobs.repository.JobPostingRepository;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.business.repository.BusinessVerificationRepository;
import com.vikisol.arena.hiring.entity.JobRequirement;
import com.vikisol.arena.hiring.repository.JobRequirementRepository;
import com.vikisol.arena.jobs.entity.SavedJob;
import com.vikisol.arena.jobs.repository.SavedJobRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobPostingRepository jobPostingRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final JobMapper jobMapper;
    private final SavedJobRepository savedJobRepository;
    private final JobRequirementRepository requirementRepository;
    private final BusinessVerificationRepository verificationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PagedResponse<JobResponse> getOpenJobs(Pageable pageable, UUID viewingUserId) {
        CandidateProfile candidate = viewingUserId == null ? null
                : candidateProfileRepository.findByUserId(viewingUserId).orElse(null);
        var page = jobPostingRepository.findByStatus(PostingStatus.OPEN, pageable);
        // One query for every posting's skills across the page (instead of one query per posting)
        // - see JobPostingRepository.findByIdInFetchingSkills for why this is a separate batched
        // call rather than folded into the findByStatus @EntityGraph.
        List<UUID> jobIds = page.getContent().stream().map(JobPosting::getId).toList();
        if (!jobIds.isEmpty()) {
            jobPostingRepository.findByIdInFetchingSkills(jobIds);
        }
        JobMapper.Extras extras = extrasFor(page.getContent(), viewingUserId);
        return PagedResponse.of(page, job -> jobMapper.toResponse(job, candidate, extras));
    }

    // A draft is only for its company team (GET /enterprise/postings/{id}); to anyone else it
    // doesn't exist.
    @Transactional(readOnly = true)
    public JobResponse getJob(UUID id, UUID viewingUserId) {
        JobPosting job = jobPostingRepository.findById(id)
                .filter(j -> j.getStatus() != PostingStatus.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + id));
        CandidateProfile candidate = viewingUserId == null ? null
                : candidateProfileRepository.findByUserId(viewingUserId).orElse(null);
        return jobMapper.toResponse(job, candidate, extrasFor(List.of(job), viewingUserId));
    }

    // Rows 22/41: bookmarks, private to the person. Saving twice is fine.
    @Transactional
    public void save(UUID userId, UUID jobId) {
        JobPosting job = jobPostingRepository.findById(jobId)
                .filter(j -> j.getStatus() != PostingStatus.DRAFT)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));
        if (savedJobRepository.findByUserIdAndPostingId(userId, jobId).isEmpty()) {
            savedJobRepository.save(SavedJob.builder().user(userRepository.getReferenceById(userId)).posting(job).build());
        }
    }

    @Transactional
    public void unsave(UUID userId, UUID jobId) {
        savedJobRepository.findByUserIdAndPostingId(userId, jobId).ifPresent(savedJobRepository::delete);
    }

    // Newest saved first. A job closed since stays listed (with its status in the job page) so
    // the person isn't left wondering where it went; a job moved back to draft drops out.
    @Transactional(readOnly = true)
    public Page<JobResponse> getSaved(UUID userId, Pageable pageable) {
        Page<SavedJob> page = savedJobRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);
        List<JobPosting> jobs = page.getContent().stream().map(SavedJob::getPosting).filter(j -> j.getStatus() != PostingStatus.DRAFT).toList();
        CandidateProfile candidate = candidateProfileRepository.findByUserId(userId).orElse(null);
        JobMapper.Extras extras = extrasFor(jobs, userId);
        return new PageImpl<>(jobs.stream().map(j -> jobMapper.toResponse(j, candidate, extras)).toList(), pageable, page.getTotalElements());
    }

    // A given list of jobs (search hits), mapped with one query per extra.
    @Transactional(readOnly = true)
    public List<JobResponse> toResponses(List<JobPosting> jobs, UUID viewingUserId) {
        CandidateProfile candidate = viewingUserId == null ? null
                : candidateProfileRepository.findByUserId(viewingUserId).orElse(null);
        JobMapper.Extras extras = extrasFor(jobs, viewingUserId);
        return jobs.stream().map(j -> jobMapper.toResponse(j, candidate, extras)).toList();
    }

    public JobMapper.Extras extrasFor(List<JobPosting> jobs, UUID viewingUserId) {
        if (jobs.isEmpty()) return new JobMapper.Extras(Map.of(), Set.of(), Set.of(), viewingUserId != null);
        List<UUID> ids = jobs.stream().map(JobPosting::getId).toList();
        Map<UUID, List<JobRequirement>> requirements = requirementRepository.findByPostingIdInOrderByKindAscPositionAsc(ids).stream()
                .collect(Collectors.groupingBy(r -> r.getPosting().getId()));
        Set<UUID> verified = new HashSet<>(verificationRepository.findVerifiedTenantIds(
                jobs.stream().map(j -> j.getEnterprise().getId()).distinct().toList()));
        Set<UUID> saved = viewingUserId == null ? Set.of() : new HashSet<>(savedJobRepository.findSavedPostingIds(viewingUserId, ids));
        return new JobMapper.Extras(requirements, verified, saved, viewingUserId != null);
    }
}
