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
