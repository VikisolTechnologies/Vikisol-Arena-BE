package com.vikisol.arena.common.geo;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

// PERFORMANCE.md: nearby reads only the cells coverCells returns, so every point inside the
// circle must fall in one of them - otherwise a post would silently vanish from the Map.
class GeohashCoverTest {

    @Test
    void everyPointInsideTheCircleIsInACoveredCell() {
        Random random = new Random(42);
        for (int round = 0; round < 400; round++) {
            double lat = -70 + random.nextDouble() * 140;
            double lng = -180 + random.nextDouble() * 360;
            double radiusKm = new double[]{0.5, 2, 5, 10, 25, 50}[random.nextInt(6)];
            List<String> cells = GeohashUtil.coverCells(lat, lng, radiusKm);
            assertThat(cells).isNotEmpty().hasSizeLessThanOrEqualTo(GeohashUtil.MAX_COVER_CELLS);
            for (int i = 0; i < 200; i++) {
                double dLat = (random.nextDouble() * 2 - 1) * radiusKm / 111.0;
                double dLng = (random.nextDouble() * 2 - 1) * radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));
                double pLat = lat + dLat, pLng = lng + dLng;
                if (pLng > 180) pLng -= 360;
                if (pLng < -180) pLng += 360;
                if (GeohashUtil.distanceKm(lat, lng, pLat, pLng) > radiusKm) continue;
                String hash = GeohashUtil.encode(pLat, pLng);
                assertThat(cells).as("point %s,%s within %s km of %s,%s", pLat, pLng, radiusKm, lat, lng)
                        .anyMatch(hash::startsWith);
            }
        }
    }

    @Test
    void smallRadiiUseFineCells() {
        assertThat(GeohashUtil.coverCells(17.44, 78.35, 1)).allMatch(c -> c.length() >= 5);
        assertThat(GeohashUtil.coverCells(17.44, 78.35, 5)).allMatch(c -> c.length() >= 4);
    }
}
