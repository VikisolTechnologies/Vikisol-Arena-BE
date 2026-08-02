package com.vikisol.arena.activity.service;

import com.vikisol.arena.activity.dto.ActivityEventResponse;
import com.vikisol.arena.activity.entity.ActivityEvent;
import com.vikisol.arena.activity.entity.ActivityEventType;
import com.vikisol.arena.activity.repository.ActivityEventRepository;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.dto.PagedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Central place every module logs a candidate-facing "what did my agent just do" event from -
 * mirrors NotificationService's role for notifications. Called by applications/interviews/
 * marketplace services rather than each one writing to ActivityEventRepository directly.
 */
@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityEventRepository activityEventRepository;

    @Transactional
    public void log(User user, ActivityEventType type, String title, String description, UUID relatedJobId, String rationale, boolean undoable) {
        activityEventRepository.save(ActivityEvent.builder()
                .user(user).type(type).title(title).description(description)
                .relatedJobId(relatedJobId).rationale(rationale).undoable(undoable)
                .build());
    }

    @Transactional(readOnly = true)
    public PagedResponse<ActivityEventResponse> getFeed(UUID userId, Pageable pageable) {
        return PagedResponse.of(activityEventRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable), this::toResponse);
    }

    private ActivityEventResponse toResponse(ActivityEvent e) {
        return new ActivityEventResponse(
                e.getId().toString(), e.getType().wireValue(), e.getTitle(), e.getDescription(),
                e.getCreatedAt().toString(), e.getRelatedJobId() == null ? null : e.getRelatedJobId().toString(),
                e.getRationale(), e.isUndoable());
    }
}
