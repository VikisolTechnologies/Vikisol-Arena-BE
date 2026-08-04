package com.vikisol.arena.platform.service;

import com.vikisol.arena.audit.AuditActions;
import com.vikisol.arena.audit.AuditService;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.platform.dto.FeatureFlagResponse;
import com.vikisol.arena.platform.dto.UpsertFeatureFlagRequest;
import com.vikisol.arena.platform.entity.FeatureFlag;
import com.vikisol.arena.platform.repository.FeatureFlagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// PA6 (feature flags/demo tools). tenantId is null on every audit write here - flag changes
// are platform-wide, not scoped to one tenant (same nullable-tenant pattern AuditEvent already
// documents for moderation review).
@Service
@RequiredArgsConstructor
public class FeatureFlagService {

    private final FeatureFlagRepository featureFlagRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<FeatureFlagResponse> list() {
        return featureFlagRepository.findAllByOrderByKeyAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public FeatureFlagResponse create(UUID actorUserId, UpsertFeatureFlagRequest request) {
        if (featureFlagRepository.findByKey(request.key()).isPresent()) {
            throw new BadRequestException("A flag with that key already exists.");
        }
        FeatureFlag flag = featureFlagRepository.save(FeatureFlag.builder()
                .key(request.key()).label(request.label()).description(request.description())
                .enabled(request.enabled()).build());
        auditService.record(null, actorUserId, AuditActions.FLAG_TOGGLED, flag.getKey() + " created (enabled=" + flag.isEnabled() + ")");
        return toResponse(flag);
    }

    @Transactional
    public FeatureFlagResponse setEnabled(UUID actorUserId, UUID id, boolean enabled) {
        FeatureFlag flag = featureFlagRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flag not found: " + id));
        flag.setEnabled(enabled);
        featureFlagRepository.save(flag);
        auditService.record(null, actorUserId, AuditActions.FLAG_TOGGLED, flag.getKey() + " -> " + enabled);
        return toResponse(flag);
    }

    private FeatureFlagResponse toResponse(FeatureFlag f) {
        return new FeatureFlagResponse(f.getId().toString(), f.getKey(), f.getLabel(), f.getDescription(), f.isEnabled());
    }
}
