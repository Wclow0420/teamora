package com.teamora.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Haversine distance used by the clock-in geofence.
 * Pins known distances so the formula can't silently drift.
 */
class GeoUtilTest {

    @Test
    void samePoint_isZero() {
        assertThat(GeoUtil.distanceMeters(3.157, 101.712, 3.157, 101.712)).isZero();
    }

    @Test
    void oneDegreeOfLatitude_isAboutOneEleventhOfEarthCircumference() {
        // 1° of latitude ≈ 111.19 km at the WGS-84 mean radius, everywhere on Earth.
        double d = GeoUtil.distanceMeters(3.0, 101.0, 4.0, 101.0);
        assertThat(d).isCloseTo(111_195.0, org.assertj.core.data.Offset.offset(50.0));
    }

    @Test
    void klccToKlTower_matchesKnownDistance() {
        // Petronas Twin Towers (KLCC) → KL Tower (Menara KL) ≈ 1.05 km.
        double d = GeoUtil.distanceMeters(3.157964, 101.711891, 3.152878, 101.703625);
        assertThat(d).isBetween(950.0, 1150.0);
    }

    @Test
    void distanceIsSymmetric() {
        double ab = GeoUtil.distanceMeters(3.10, 101.60, 3.20, 101.70);
        double ba = GeoUtil.distanceMeters(3.20, 101.70, 3.10, 101.60);
        assertThat(ab).isCloseTo(ba, org.assertj.core.data.Offset.offset(0.001));
    }
}
