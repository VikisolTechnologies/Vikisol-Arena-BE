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

import java.util.Map;
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

    private final com.vikisol.arena.follows.repository.FollowRepository followRepository;
    private final EnterpriseProfileRepository enterpriseProfileRepository;
    private final JobPostingRepository jobPostingRepository;
    private final JobMapper jobMapper;
    private final com.vikisol.arena.jobs.service.JobService jobService;
    private final FollowService followService;

    @Transactional(readOnly = true)
    public PagedResponse<CompanyResponse> listCompanies(String query, UUID viewingUserId, Pageable pageable) {
        String q = (query == null || query.isBlank()) ? "" : query.trim();
        var page = enterpriseProfileRepository.search(q, pageable);
        Map<UUID, CompanyResponse> mapped = toResponses(page.getContent(), viewingUserId).stream()
                .collect(java.util.stream.Collectors.toMap(r -> UUID.fromString(r.id()), r -> r));
        return PagedResponse.of(page, c -> mapped.get(c.getId()));
    }

    // A list of company cards with every count fetched in one query each (PERFORMANCE.md: was
    // three queries per company).
    @Transactional(readOnly = true)
    public java.util.List<CompanyResponse> toResponses(java.util.List<EnterpriseProfile> companies, UUID viewingUserId) {
        if (companies.isEmpty()) return java.util.List.of();
        java.util.List<UUID> ids = companies.stream().map(EnterpriseProfile::getId).toList();
        Map<UUID, Long> jobs = counts(jobPostingRepository.countByEnterpriseIdsAndStatusIn(ids, java.util.List.of(PostingStatus.OPEN, PostingStatus.PAUSED)));
        Map<UUID, Long> followers = counts(followRepository.countFollowersByCompanyIds(ids));
        java.util.Set<UUID> mine = viewingUserId == null ? java.util.Set.of()
                : new java.util.HashSet<>(followRepository.findFollowingCompanyIdsByFollowerUserId(viewingUserId));
        return companies.stream().map(c -> new CompanyResponse(c.getId().toString(), c.getCompanyName(), c.getLogoEmoji(),
                c.getIndustry().wireValue(), c.getSize().wireValue(), jobs.getOrDefault(c.getId(), 0L).intValue(),
                followers.getOrDefault(c.getId(), 0L), viewingUserId == null ? null : mine.contains(c.getId()))).toList();
    }

    private static Map<UUID, Long> counts(java.util.List<Object[]> rows) {
        Map<UUID, Long> out = new java.util.HashMap<>();
        for (Object[] r : rows) out.put((UUID) r[0], (Long) r[1]);
        return out;
    }

    @Transactional(readOnly = true)
    public CompanyResponse getCompany(UUID companyId, UUID viewingUserId) {
        return toResponse(requireCompany(companyId), viewingUserId);
    }

    @Transactional(readOnly = true)
    public PagedResponse<JobResponse> getCompanyJobs(UUID companyId, Pageable pageable) {
        EnterpriseProfile company = requireCompany(companyId);
        var page = jobPostingRepository.findByEnterpriseAndStatusNot(company, PostingStatus.DRAFT, pageable);
        JobMapper.Extras extras = jobService.extrasFor(page.getContent(), null);
        return PagedResponse.of(page, j -> jobMapper.toResponse(j, null, extras));
    }

    private CompanyResponse toResponse(EnterpriseProfile company, UUID viewingUserId) {
        int openJobCount = (int) jobPostingRepository.countByEnterpriseAndStatusIn(company, java.util.List.of(PostingStatus.OPEN, PostingStatus.PAUSED));
        long followerCount = followService.getCompanyFollowerCount(company.getId());
        Boolean viewerFollows = viewingUserId == null ? null : followService.viewerFollowsCompany(viewingUserId, company.getId());
        return new CompanyResponse(company.getId().toString(), company.getCompanyName(), company.getLogoEmoji(),
                company.getIndustry().wireValue(), company.getSize().wireValue(), openJobCount, followerCount, viewerFollows);
    }

    private EnterpriseProfile requireCompany(UUID id) {
        return enterpriseProfileRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Company not found: " + id));
    }
}
