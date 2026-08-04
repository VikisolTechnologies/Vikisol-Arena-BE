package com.vikisol.arena.platform.service;

import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.enterprise.repository.MembershipRepository;
import com.vikisol.arena.platform.dto.PlatformUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// PA3 (global user search, across talent and every enterprise role).
@Service
@RequiredArgsConstructor
public class PlatformUserService {

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;

    @Transactional(readOnly = true)
    public PagedResponse<PlatformUserResponse> search(String query, String roleWire, Pageable pageable) {
        String q = query == null ? "" : query.trim();
        Role role = (roleWire == null || roleWire.isBlank()) ? null : Role.fromWireValue(roleWire);
        return PagedResponse.of(userRepository.search(q, role, pageable), this::toResponse);
    }

    private PlatformUserResponse toResponse(User u) {
        String tenantId = null;
        String tenantName = null;
        if (u.getRole().hasTenant()) {
            var membership = membershipRepository.findByUserId(u.getId());
            if (membership.isPresent()) {
                tenantId = membership.get().getTenant().getId().toString();
                tenantName = membership.get().getTenant().getCompanyName();
            }
        }
        return new PlatformUserResponse(u.getId().toString(), u.getName(), u.getEmail(), u.getRole().wireValue(),
                tenantId, tenantName, u.getCreatedAt().toString());
    }
}
