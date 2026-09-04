package com.vikisol.arena.landing.dto;

import java.util.List;

public record LandingStatsResponse(
        long openToWorkCount,
        List<IndustryStat> byIndustry,
        long openProjectCount
) {
}
