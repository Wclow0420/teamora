package com.teamora.common;

/** Geospatial helpers. */
public final class GeoUtil {

    /** Mean Earth radius in metres (WGS-84 spherical approximation). */
    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private GeoUtil() {}

    /**
     * Great-circle distance in metres between two WGS-84 coordinates, via the
     * Haversine formula. Accurate to well within a few metres at city scale —
     * more than enough for a clock-in geofence radius (min 50 m).
     */
    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_M * c;
    }
}
