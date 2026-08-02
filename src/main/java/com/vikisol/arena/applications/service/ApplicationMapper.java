package com.vikisol.arena.applications.service;

import com.vikisol.arena.applications.dto.ApplicationResponse;
import com.vikisol.arena.applications.entity.Application;
import org.springframework.stereotype.Component;

@Component
public class ApplicationMapper {

    public ApplicationResponse toResponse(Application a) {
        return new ApplicationResponse(
                a.getId().toString(),
                a.getJobPosting().getId().toString(),
                a.getStage().wireValue(),
                a.getAppliedAt().toString(),
                a.getUpdatedAt().toString()
        );
    }
}
