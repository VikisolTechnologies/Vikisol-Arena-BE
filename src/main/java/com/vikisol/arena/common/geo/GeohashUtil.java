package com.vikisol.arena.common.geo;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Self-contained geohash encode/decode + Haversine distance, deliberately not a new Maven
 * dependency (see DECISIONS.md - avoiding PostGIS/a spatial library for what "coarse nearby
 * discovery" actually needs). Standard base32 geohash algorithm, ~9-character precision
 * (roughly 5m cells) truncated to {@link #DISCOVERY_PRECISION} (7 chars, ~150m cells) for
 * storage - coarse by construction, not just coarse by convention.
 */
public final class GeohashUtil {

    private static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";
    /** ~150m cells - coarse enough that the stored value alone is never a precise point. */
    public static final int DISCOVERY_PRECISION = 7;
    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeohashUtil() {
    }

    public static String encode(double lat, double lng) {
        return encode(lat, lng, DISCOVERY_PRECISION);
    }

    public static String encode(double lat, double lng, int precision) {
        double[] latRange = {-90.0, 90.0};
        double[] lngRange = {-180.0, 180.0};
        StringBuilder hash = new StringBuilder();
        boolean evenBit = true;
        int bit = 0, ch = 0;

        while (hash.length() < precision) {
            double mid;
            if (evenBit) {
                mid = (lngRange[0] + lngRange[1]) / 2;
                if (lng >= mid) { ch |= (1 << (4 - bit)); lngRange[0] = mid; } else { lngRange[1] = mid; }
            } else {
                mid = (latRange[0] + latRange[1]) / 2;
                if (lat >= mid) { ch |= (1 << (4 - bit)); latRange[0] = mid; } else { latRange[1] = mid; }
            }
            evenBit = !evenBit;
            if (bit < 4) {
                bit++;
            } else {
                hash.append(BASE32.charAt(ch));
                bit = 0;
                ch = 0;
            }
        }
        return hash.toString();
    }

    static final int MAX_COVER_CELLS = 25;

    /**
     * The geohash prefixes whose cells cover a circle's bounding box, at the finest precision that
     * needs at most {@value #MAX_COVER_CELLS} cells. Finer cells mean fewer posts outside the
     * circle to read and discard. Empty when even the coarsest cells would need more, meaning "no
     * prefix narrows this".
     */
    public static java.util.List<String> coverCells(double lat, double lng, double radiusKm) {
        double dLat = radiusKm / 111.0;
        double dLng = radiusKm / (111.0 * Math.max(0.01, Math.cos(Math.toRadians(Math.min(89.0, Math.abs(lat) + dLat)))));
        double south = Math.max(-89.999999, lat - dLat), north = Math.min(89.999999, lat + dLat);
        for (int p = DISCOVERY_PRECISION; p >= 1; p--) {
            int bits = 5 * p;
            double cellLat = 180.0 / Math.pow(2, bits / 2);
            double cellLng = 360.0 / Math.pow(2, bits - bits / 2);
            long rows = (long) (Math.floor((north + 90) / cellLat) - Math.floor((south + 90) / cellLat)) + 1;
            long cols = (long) (Math.floor((lng + dLng + 180) / cellLng) - Math.floor((lng - dLng + 180) / cellLng)) + 1;
            if (rows * cols > MAX_COVER_CELLS) continue;
            java.util.LinkedHashSet<String> cells = new java.util.LinkedHashSet<>();
            for (long r = 0; r < rows; r++) {
                double y = Math.min(89.999999, south + r * cellLat);
                for (long c = 0; c < cols; c++) {
                    double x = lng - dLng + c * cellLng;
                    if (x > 180) x -= 360;
                    if (x < -180) x += 360;
                    cells.add(encode(y, x, p));
                }
            }
            // The last row/column (north and east edges) in case stepping fell short of them.
            for (double y : new double[]{south, north}) {
                for (double x : new double[]{lng - dLng, lng + dLng}) {
                    double xx = x > 180 ? x - 360 : x < -180 ? x + 360 : x;
                    cells.add(encode(y, xx, p));
                }
            }
            return java.util.List.copyOf(cells);
        }
        return java.util.List.of();
    }

    /** Decodes to the CENTER of the geohash cell - this is the "approximation" every stored
     * coordinate in this codebase actually is, per DECISIONS.md's location entry. */
    public static double[] decode(String geohash) {
        double[] latRange = {-90.0, 90.0};
        double[] lngRange = {-180.0, 180.0};
        boolean evenBit = true;

        for (int i = 0; i < geohash.length(); i++) {
            int idx = BASE32.indexOf(geohash.charAt(i));
            for (int n = 4; n >= 0; n--) {
                int bit = (idx >> n) & 1;
                if (evenBit) {
                    double mid = (lngRange[0] + lngRange[1]) / 2;
                    if (bit == 1) lngRange[0] = mid; else lngRange[1] = mid;
                } else {
                    double mid = (latRange[0] + latRange[1]) / 2;
                    if (bit == 1) latRange[0] = mid; else latRange[1] = mid;
                }
                evenBit = !evenBit;
            }
        }
        return new double[]{(latRange[0] + latRange[1]) / 2, (lngRange[0] + lngRange[1]) / 2};
    }

    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    /** A second, independent random jitter applied at serve time (map-pin display only) on
     * top of the already-coarse stored value - see DECISIONS.md. Deterministic per call, not
     * per-post-stable; callers that need a stable-but-jittered pin should seed accordingly. */
    public static double[] jitter(double lat, double lng, double maxMeters) {
        double radiusInDegrees = maxMeters / 111_320.0; // ~meters per degree of latitude
        double u = ThreadLocalRandom.current().nextDouble();
        double v = ThreadLocalRandom.current().nextDouble();
        double w = radiusInDegrees * Math.sqrt(u);
        double t = 2 * Math.PI * v;
        double dLat = w * Math.cos(t);
        double dLng = (w * Math.sin(t)) / Math.cos(Math.toRadians(lat));
        return new double[]{lat + dLat, lng + dLng};
    }
}
