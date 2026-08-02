package com.vikisol.arena.activity.controller;

import com.vikisol.arena.activity.dto.ActivityEventResponse;
import com.vikisol.arena.activity.service.ActivityService;
import com.vikisol.arena.common.dto.ApiResponse;
import com.vikisol.arena.common.dto.PagedResponse;
import com.vikisol.arena.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ActivityEventResponse>>> getFeed(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.ok(activityService.getFeed(principal.getId(), pageable)));
    }
}
