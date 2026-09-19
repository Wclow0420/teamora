package com.teamora.payroll;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Malaysian statutory payroll calculator. These pin the exact
 * sen so the documented method (EPF 11% rounded up to the ringgit; SOCSO/EIS on the
 * RM100 band midpoint at gazetted rates; OT and bonus excluded from SOCSO/EIS;
 * claims excluded from every statutory base) can't silently drift.
 */
class PayrollCalculatorTest {

    private static BigDecimal rm(String v) {
        return new BigDecimal(v);
    }

    @Test
    void statutory_basic4000_matchesSeededDemoFigures() {
        // Amir's RM4,000 basic — must reproduce the demo payslip figures.
        PayrollCalculator.Statutory s = PayrollCalculator.statutory(rm("4000"), BigDecimal.ZERO);
        assertThat(s.epfEmployee()).isEqualByComparingTo("440.00");
        assertThat(s.epfEmployer()).isEqualByComparingTo("520.00"); // wage ≤ 5000 → 13%
        assertThat(s.socsoEmployee()).isEqualByComparingTo("19.75"); // 0.5% of 3950 band-midpoint
        assertThat(s.socsoEmployer()).isEqualByComparingTo("69.13"); // 1.75% of 3950
        assertThat(s.eisEmployee()).isEqualByComparingTo("7.90");    // 0.2% of 3950
        assertThat(s.eisEmployer()).isEqualByComparingTo("7.90");
    }

    @Test
    void epf_roundsUpToNextRinggit() {
        // 11% of 4520 = 497.20 → rounded up to 498.
        PayrollCalculator.Statutory s = PayrollCalculator.statutory(rm("4520"), BigDecimal.ZERO);
        assertThat(s.epfEmployee()).isEqualByComparingTo("498.00");
    }

    @Test
    void epf_employerRateDropsTo12PercentAbove5000() {
        PayrollCalculator.Statutory s = PayrollCalculator.statutory(rm("7000"), BigDecimal.ZERO);
        assertThat(s.epfEmployee()).isEqualByComparingTo("770.00");  // 11%
        assertThat(s.epfEmployer()).isEqualByComparingTo("840.00");  // 12% (> 5000)
    }

    @Test
    void socsoEis_cappedAtCeilingBand() {
        // Wages above RM5,900 contribute as the RM5,950 top band, even above the RM6,000 ceiling.
        PayrollCalculator.Statutory s = PayrollCalculator.statutory(rm("6500"), BigDecimal.ZERO);
        assertThat(s.socsoEmployee()).isEqualByComparingTo("29.75"); // 0.5% of 5950
        assertThat(s.eisEmployee()).isEqualByComparingTo("11.90");   // 0.2% of 5950
    }

    @Test
    void bonus_raisesEpfWageButNotSocso() {
        // EPF wage = basic + bonus; SOCSO/EIS wage = basic only.
        PayrollCalculator.Statutory s = PayrollCalculator.statutory(rm("4000"), rm("1000"));
        assertThat(s.epfEmployee()).isEqualByComparingTo("550.00");  // 11% of 5000
        assertThat(s.epfEmployer()).isEqualByComparingTo("650.00");  // 13% of 5000 (≤ 5000)
        assertThat(s.socsoEmployee()).isEqualByComparingTo("19.75"); // still on 4000 → band 3950
    }

    @Test
    void compute_basicOnly_single_includesPcb() {
        // Single, RM4,000 basic: chargeable 48000 − 4000 EPF − 9000 = 35000 → tax 600 → PCB 50/mo.
        PayrollCalculator.Breakdown b = PayrollCalculator.compute(
                rm("4000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, PayrollCalculator.TaxProfile.SINGLE);
        assertThat(b.gross()).isEqualByComparingTo("4000.00");
        assertThat(b.pcb()).isEqualByComparingTo("50.00");
        assertThat(b.deductions()).isEqualByComparingTo("517.65"); // 440 + 19.75 + 7.90 + 50
        assertThat(b.net()).isEqualByComparingTo("3482.35");
    }

    @Test
    void compute_overtimeAndClaims_addToGrossButNotStatutoryOrPcb() {
        // OT (300) and claims (500) lift gross + net but never the statutory/PCB deductions.
        PayrollCalculator.Breakdown b = PayrollCalculator.compute(
                rm("4000"), rm("300"), rm("500"), BigDecimal.ZERO, PayrollCalculator.TaxProfile.SINGLE);
        assertThat(b.gross()).isEqualByComparingTo("4800.00");      // 4000 + 300 + 500
        assertThat(b.deductions()).isEqualByComparingTo("517.65");  // unchanged from basic-only
        assertThat(b.net()).isEqualByComparingTo("4282.35");        // 4800 - 517.65
    }

    @Test
    void compute_handlesNullsAsZero() {
        PayrollCalculator.Breakdown b = PayrollCalculator.compute(
                rm("3000"), null, null, null, PayrollCalculator.TaxProfile.SINGLE);
        assertThat(b.overtime()).isEqualByComparingTo("0.00");
        assertThat(b.claims()).isEqualByComparingTo("0.00");
        assertThat(b.gross()).isEqualByComparingTo("3000.00");
    }

    // ---- PCB / MTD ----

    @Test
    void annualIncomeTax_matchesBracketCumulatives() {
        assertThat(PayrollCalculator.annualIncomeTax(rm("5000"))).isEqualByComparingTo("0.00");
        assertThat(PayrollCalculator.annualIncomeTax(rm("35000"))).isEqualByComparingTo("600.00");
        assertThat(PayrollCalculator.annualIncomeTax(rm("70000"))).isEqualByComparingTo("3700.00");
        assertThat(PayrollCalculator.annualIncomeTax(rm("100000"))).isEqualByComparingTo("9400.00");
        assertThat(PayrollCalculator.annualIncomeTax(BigDecimal.ZERO)).isEqualByComparingTo("0.00");
    }

    @Test
    void pcb_single_midEarner() {
        BigDecimal epf = PayrollCalculator.statutory(rm("4000"), BigDecimal.ZERO).epfEmployee();
        assertThat(PayrollCalculator.monthlyPcb(rm("4000"), epf, PayrollCalculator.TaxProfile.SINGLE))
                .isEqualByComparingTo("50.00");
    }

    @Test
    void pcb_marriedWithChildren_lowerThanSingle() {
        // Married, non-working spouse, 2 children: +RM4,000 spouse +RM4,000 child relief.
        // chargeable 48000 − 4000 − 17000 = 27000 → tax 360 → PCB 30/mo.
        BigDecimal epf = PayrollCalculator.statutory(rm("4000"), BigDecimal.ZERO).epfEmployee();
        assertThat(PayrollCalculator.monthlyPcb(rm("4000"), epf, new PayrollCalculator.TaxProfile(true, false, 2)))
                .isEqualByComparingTo("30.00");
    }

    @Test
    void pcb_workingSpouse_getsNoSpouseRelief() {
        BigDecimal epf = PayrollCalculator.statutory(rm("4000"), BigDecimal.ZERO).epfEmployee();
        // Married but spouse working → no spouse relief → same as single (50.00).
        assertThat(PayrollCalculator.monthlyPcb(rm("4000"), epf, new PayrollCalculator.TaxProfile(true, true, 0)))
                .isEqualByComparingTo("50.00");
    }

    @Test
    void pcb_lowEarner_isZero() {
        // RM1,100 basic, single: chargeable falls below the taxable threshold → no PCB.
        BigDecimal epf = PayrollCalculator.statutory(rm("1100"), BigDecimal.ZERO).epfEmployee();
        assertThat(PayrollCalculator.monthlyPcb(rm("1100"), epf, PayrollCalculator.TaxProfile.SINGLE))
                .isEqualByComparingTo("0.00");
    }
}
