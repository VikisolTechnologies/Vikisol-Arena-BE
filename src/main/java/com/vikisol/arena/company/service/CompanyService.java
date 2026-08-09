package com.vikisol.arena.company.service;

import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.company.dto.CompanyResponse;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import com.vikisol.arena.follows.service.FollowService;
import com.vikisol.arena.jobs.dto.JobResponse;
import com.vikisol.arena.jobs.entity.PostingStatus;
import com.vikisol.arena.jobs.repository.JobPostingRepository;
import com.vikisol.arena.jobs.service.JobMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * ARENA-V2-PRODUCT-ARCHITECTURE.md Phase C "company pages" - a talent-facing read layer over
 * the existing {@link EnterpriseProfile} tenant root, the same "same entity, narrower public
 * DTO" pattern {@code TalentSearchService.redactIfLocked} already uses in the opposite direction
 * (enterprise viewers seeing a redacted candidate profile). No new Company entity - see
 * DECISIONS.md.
 */
@Service
@RequiredArgsConstructor
public class CompanyService {

    private final EnterpriseProfileRepository enterpriseProfileRepository;
    private final JobPostingRepository jobPostingRepository;
    private final JobMapper jobMapper;
    private final FollowService followService;

    @Transactional(readOnly = true)
    public PagedResponse<CompanyResponse> listCompanies(String query, UUID viewingUserId, Pageable pageable) {
        String q = (query == null || query.isBlank()) ? "" : query.trim();
        var page = enterpriseProfileRepository.search(q, pageable);
        return PagedResponse.of(page, c -> toResponse(c, viewingUserId));
    }

    @Transactional(readOnly = true)
    public CompanyResponse getCompany(UUID companyId, UUID viewingUserId) {
        return toResponse(requireCompany(companyId), viewingUserId);
    }

    @Transactional(readOnly = true)
    public PagedResponse<JobResponse> getCompanyJobs(UUID companyId, Pageable pageable) {
        EnterpriseProfile company = requireCompany(companyId);
        var page = jobPostingRepository.findByEnterprise(company, pageable);
        return PagedResponse.of(page, j -> jobMapper.toResponse(j, null));
    }

    private CompanyResponse toResponse(EnterpriseProfile company, UUID viewingUserId) {
        int openJobCount = (int) jobPostingRepository.countByEnterpriseAndStatusNot(company, PostingStatus.CLOSED);
        long followerCount = followService.getCompanyFollowerCount(company.getId());
        Boolean viewerFollows = viewingUserId == null ? null : followService.viewerFollowsCompany(viewingUserId, company.getId());
        return new CompanyResponse(company.getId().toString(), company.getCompanyName(), company.getLogoEmoji(),
                company.getIndustry().wireValue(), company.getSize().wireValue(), openJobCount, followerCount, viewerFollows);
    }

    private EnterpriseProfile requireCompany(UUID id) {
        return enterpriseProfileRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Company not found: " + id));
    }
}
