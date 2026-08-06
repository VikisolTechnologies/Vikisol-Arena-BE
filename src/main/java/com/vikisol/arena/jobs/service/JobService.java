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

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobPostingRepository jobPostingRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final JobMapper jobMapper;

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
        return PagedResponse.of(page, job -> jobMapper.toResponse(job, candidate));
    }

    @Transactional(readOnly = true)
    public JobResponse getJob(UUID id, UUID viewingUserId) {
        JobPosting job = jobPostingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + id));
        CandidateProfile candidate = viewingUserId == null ? null
                : candidateProfileRepository.findByUserId(viewingUserId).orElse(null);
        return jobMapper.toResponse(job, candidate);
    }
}
