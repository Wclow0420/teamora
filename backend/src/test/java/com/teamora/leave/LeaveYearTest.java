package com.teamora.leave;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The leave-year window. A leave year is named after the calendar year it STARTS in,
 * which is what makes an April–March year unambiguous.
 */
class LeaveYearTest {

    // ---------- start month 1 (calendar year) ----------

    @Test
    void januaryStart_isTheCalendarYear() {
        assertThat(LeaveYear.yearOf(LocalDate.of(2026, 1, 1), 1)).isEqualTo(2026);
        assertThat(LeaveYear.yearOf(LocalDate.of(2026, 6, 15), 1)).isEqualTo(2026);
        assertThat(LeaveYear.yearOf(LocalDate.of(2026, 12, 31), 1)).isEqualTo(2026);
        assertThat(LeaveYear.yearOf(LocalDate.of(2027, 1, 1), 1)).isEqualTo(2027);

        assertThat(LeaveYear.startOf(2026, 1)).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(LeaveYear.endOf(2026, 1)).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void januaryStart_monthIndexIsZeroBasedFromJanuary() {
        assertThat(LeaveYear.monthIndexWithinYear(LocalDate.of(2026, 1, 9), 1)).isZero();
        assertThat(LeaveYear.monthIndexWithinYear(LocalDate.of(2026, 9, 24), 1)).isEqualTo(8);
        assertThat(LeaveYear.monthIndexWithinYear(LocalDate.of(2026, 12, 31), 1)).isEqualTo(11);
    }

    // ---------- start month 4 (April–March) ----------

    @Test
    void aprilStart_runsAprilToMarchAndIsNamedAfterItsStartYear() {
        // 31 Mar 2026 still belongs to leave year 2025 (Apr 2025 → Mar 2026).
        assertThat(LeaveYear.yearOf(LocalDate.of(2026, 3, 31), 4)).isEqualTo(2025);
        assertThat(LeaveYear.yearOf(LocalDate.of(2026, 4, 1), 4)).isEqualTo(2026);
        assertThat(LeaveYear.yearOf(LocalDate.of(2026, 12, 31), 4)).isEqualTo(2026);
        // …and January the following year is STILL leave year 2026.
        assertThat(LeaveYear.yearOf(LocalDate.of(2027, 1, 1), 4)).isEqualTo(2026);
        assertThat(LeaveYear.yearOf(LocalDate.of(2027, 4, 1), 4)).isEqualTo(2027);

        assertThat(LeaveYear.startOf(2026, 4)).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(LeaveYear.endOf(2026, 4)).isEqualTo(LocalDate.of(2027, 3, 31));
    }

    @Test
    void aprilStart_monthIndexWrapsThroughDecemberIntoJanuary() {
        assertThat(LeaveYear.monthIndexWithinYear(LocalDate.of(2026, 4, 1), 4)).isZero();
        assertThat(LeaveYear.monthIndexWithinYear(LocalDate.of(2026, 12, 31), 4)).isEqualTo(8);
        assertThat(LeaveYear.monthIndexWithinYear(LocalDate.of(2027, 1, 1), 4)).isEqualTo(9);
        assertThat(LeaveYear.monthIndexWithinYear(LocalDate.of(2027, 3, 31), 4)).isEqualTo(11);
    }

    // ---------- guards ----------

    @Test
    void containsMatchesYearOf() {
        assertThat(LeaveYear.contains(2025, 4, LocalDate.of(2026, 3, 31))).isTrue();
        assertThat(LeaveYear.contains(2026, 4, LocalDate.of(2026, 3, 31))).isFalse();
    }

    @Test
    void anOutOfRangeOrMissingStartMonthFallsBackToJanuary() {
        assertThat(LeaveYear.normaliseStartMonth(null)).isEqualTo(1);
        assertThat(LeaveYear.normaliseStartMonth(0)).isEqualTo(1);
        assertThat(LeaveYear.normaliseStartMonth(13)).isEqualTo(1);
        assertThat(LeaveYear.normaliseStartMonth(4)).isEqualTo(4);
    }
}
