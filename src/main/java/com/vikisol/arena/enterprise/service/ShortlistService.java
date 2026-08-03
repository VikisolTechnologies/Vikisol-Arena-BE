package com.vikisol.arena.enterprise.service;

import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.entity.ShortlistEntry;
import com.vikisol.arena.enterprise.repository.ShortlistEntryRepository;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShortlistService {

    private final ShortlistEntryRepository shortlistEntryRepository;
    private final EnterpriseProfileService enterpriseProfileService;
    private final CandidateProfileRepository candidateProfileRepository;

    @Transactional(readOnly = true)
    public List<String> getShortlistIds(UUID enterpriseUserId) {
        EnterpriseProfile enterprise = requireEnterprise(enterpriseUserId);
        return shortlistEntryRepository.findByEnterpriseId(enterprise.getId()).stream()
                .map(e -> e.getCandidate().getId().toString()).toList();
    }

    @Transactional
    public List<String> toggle(UUID enterpriseUserId, UUID candidateId) {
        EnterpriseProfile enterprise = requireEnterprise(enterpriseUserId);
        var existing = shortlistEntryRepository.findByEnterpriseIdAndCandidateId(enterprise.getId(), candidateId);
        if (existing.isPresent()) {
            shortlistEntryRepository.delete(existing.get());
        } else {
            CandidateProfile candidate = candidateProfileRepository.findById(candidateId)
                    .orElseThrow(() -> new ResourceNotFoundException("Candidate not found: " + candidateId));
            shortlistEntryRepository.save(ShortlistEntry.builder().enterprise(enterprise).candidate(candidate).build());
        }
        return getShortlistIds(enterpriseUserId);
    }

    private EnterpriseProfile requireEnterprise(UUID userId) {
        return enterpriseProfileService.getEntityForUser(userId);
    }
}
