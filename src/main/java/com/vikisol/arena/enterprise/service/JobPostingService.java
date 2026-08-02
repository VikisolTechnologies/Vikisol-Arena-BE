package com.vikisol.arena.enterprise.service;

import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.enterprise.dto.CreatePostingRequest;
import com.vikisol.arena.enterprise.dto.JobPostingResponse;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import com.vikisol.arena.jobs.entity.EmploymentType;
import com.vikisol.arena.jobs.entity.JobPosting;
import com.vikisol.arena.jobs.entity.PostingStatus;
import com.vikisol.arena.jobs.repository.JobPostingRepository;
import com.vikisol.arena.profile.entity.Industry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobPostingService {

    private final JobPostingRepository jobPostingRepository;
    private final EnterpriseProfileRepository enterpriseProfileRepository;
    private final JobPostingMapper mapper;

    @Transactional(readOnly = true)
    public PagedResponse<JobPostingResponse> getMyPostings(UUID userId, Pageable pageable) {
        EnterpriseProfile enterprise = requireEnterprise(userId);
        return PagedResponse.of(jobPostingRepository.findByEnterprise(enterprise, pageable), mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public JobPostingResponse getPosting(UUID id) {
        return mapper.toResponse(requirePosting(id));
    }

    @Transactional
    public JobPostingResponse createPosting(UUID userId, CreatePostingRequest request) {
        EnterpriseProfile enterprise = requireEnterprise(userId);
        JobPosting posting = JobPosting.builder()
                .enterprise(enterprise)
                .title(request.title())
                .industry(Industry.fromWireValue(request.industry()))
                .location(request.location())
                .remote(request.remote())
                .employmentType(EmploymentType.fromWireValue(request.employmentType()))
                .salaryMin(request.salaryMin())
                .salaryMax(request.salaryMax())
                .skills(request.skills())
                .description(request.description())
                .status(PostingStatus.OPEN)
                .build();
        return mapper.toResponse(jobPostingRepository.save(posting));
    }

    @Transactional
    public void setStatus(UUID userId, UUID postingId, PostingStatus status) {
        JobPosting posting = requirePosting(postingId);
        if (!posting.getEnterprise().getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Not your posting");
        }
        posting.setStatus(status);
        jobPostingRepository.save(posting);
    }

    private JobPosting requirePosting(UUID id) {
        return jobPostingRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Posting not found: " + id));
    }

    private EnterpriseProfile requireEnterprise(UUID userId) {
        return enterpriseProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No enterprise profile for this account"));
    }
}
