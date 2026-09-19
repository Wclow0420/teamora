package com.teamora.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Malaysian statutory payroll calculator (Peninsular, Category 1 — employee under
 * 60, citizen/PR). Pure and deterministic so it can be unit-tested to the sen.
 *
 * <p><b>Method (real computation — documented honestly, not placeholder figures):</b>
 * <ul>
 *   <li><b>Wage bases.</b> EPF wages = basic + bonus. SOCSO/EIS wages = basic.
 *       <b>Overtime is excluded from every statutory base</b> (EPF/SOCSO/EIS Acts
 *       exclude OT), and bonus is excluded from SOCSO/EIS. Expense <b>claims</b>
 *       reimbursements are not wages and never enter a statutory base.</li>
 *   <li><b>EPF (KWSP).</b> Employee 11%; employer 13% when EPF wage ≤ RM5,000 else
 *       12% — each rounded <b>up to the next whole ringgit</b> (Third Schedule
 *       convention).</li>
 *   <li><b>SOCSO (PERKESO, Cat 1) & EIS (SIP).</b> Employee 0.5% / employer 1.75%
 *       (SOCSO) and 0.2% / 0.2% (EIS), applied to the <b>RM100 wage-band midpoint</b>
 *       capped at the RM6,000 ceiling (top band assumed RM5,950) — i.e. the PERKESO
 *       contribution-table method at the gazetted rates. May differ by a few sen
 *       from the table's 5-sen rounding.</li>
 *   <li><b>PCB/MTD (monthly income tax)</b> is <b>estimated</b> via the LHDN
 *       computerised method on YA2024 brackets + reliefs (individual RM9,000;
 *       spouse RM4,000 when married with a non-working spouse; RM2,000/child;
 *       EPF relief capped RM4,000/yr). It annualises the month's <b>basic</b>
 *       salary (variable OT/claims are not annualised) and does <b>not</b> carry
 *       year-to-date accumulation or prior PCB — so it's a good per-month estimate,
 *       not a tax filing. Labelled as an estimate in the UI.</li>
 * </ul>
 */
public final class PayrollCalculator {

    private PayrollCalculator() {
    }

    private static final BigDecimal EPF_EMPLOYEE_RATE = new BigDecimal("0.11");
    private static final BigDecimal EPF_EMPLOYER_RATE_LOW = new BigDecimal("0.13");  // wage ≤ 5000
    private static final BigDecimal EPF_EMPLOYER_RATE_HIGH = new BigDecimal("0.12"); // wage > 5000
    private static final BigDecimal EPF_EMPLOYER_THRESHOLD = new BigDecimal("5000");

    private static final BigDecimal SOCSO_EMPLOYEE_RATE = new BigDecimal("0.005");
    private static final BigDecimal SOCSO_EMPLOYER_RATE = new BigDecimal("0.0175");
    private static final BigDecimal EIS_RATE = new BigDecimal("0.002");

    private static final BigDecimal CONTRIBUTION_CEILING = new BigDecimal("6000");
    private static final BigDecimal TOP_BAND_MIDPOINT = new BigDecimal("5950");
    private static final BigDecimal TOP_BAND_LOWER = new BigDecimal("5900");

    // PCB/MTD — YA2024 reliefs + bracket schedule.
    private static final BigDecimal MONTHS = new BigDecimal("12");
    private static final BigDecimal INDIVIDUAL_RELIEF = new BigDecimal("9000");
    private static final BigDecimal SPOUSE_RELIEF = new BigDecimal("4000");
    private static final BigDecimal CHILD_RELIEF = new BigDecimal("2000");
    private static final BigDecimal EPF_RELIEF_CAP = new BigDecimal("4000"); // per year

    /** YA2024 resident individual tax brackets: lower bound (inclusive) of each band + its marginal rate. */
    private static final BigDecimal[] TAX_BAND_LOWER = {
            new BigDecimal("0"), new BigDecimal("5000"), new BigDecimal("20000"), new BigDecimal("35000"),
            new BigDecimal("50000"), new BigDecimal("70000"), new BigDecimal("100000"),
            new BigDecimal("400000"), new BigDecimal("600000"), new BigDecimal("2000000")
    };
    private static final BigDecimal[] TAX_BAND_RATE = {
            new BigDecimal("0.00"), new BigDecimal("0.01"), new BigDecimal("0.03"), new BigDecimal("0.06"),
            new BigDecimal("0.11"), new BigDecimal("0.19"), new BigDecimal("0.25"),
            new BigDecimal("0.26"), new BigDecimal("0.28"), new BigDecimal("0.30")
    };

    /** Employee tax profile for PCB/MTD. */
    public record TaxProfile(boolean married, boolean spouseWorking, int children) {
        public static final TaxProfile SINGLE = new TaxProfile(false, false, 0);
    }

    /** The six statutory contribution amounts for one employee in one period. */
    public record Statutory(
            BigDecimal epfEmployee, BigDecimal epfEmployer,
            BigDecimal socsoEmployee, BigDecimal socsoEmployer,
            BigDecimal eisEmployee, BigDecimal eisEmployer
    ) {}

    /** A fully-computed monthly payslip breakdown (all employee-facing amounts). */
    public record Breakdown(
            BigDecimal basic, BigDecimal overtime, BigDecimal claims, BigDecimal bonus,
            BigDecimal gross,
            BigDecimal epf, BigDecimal socso, BigDecimal eis, BigDecimal pcb,
            BigDecimal deductions, BigDecimal net
    ) {}

    /**
     * Compute the employee + employer statutory contributions.
     *
     * @param basic monthly basic salary (the SOCSO/EIS base)
     * @param bonus cash bonus for the period (EPF base only)
     */
    public static Statutory statutory(BigDecimal basic, BigDecimal bonus) {
        BigDecimal b = nz(basic);
        BigDecimal epfWage = b.add(nz(bonus));
        BigDecimal socsoEisWage = b;

        BigDecimal epfEmployee = ceilRinggit(epfWage.multiply(EPF_EMPLOYEE_RATE));
        BigDecimal employerRate = epfWage.compareTo(EPF_EMPLOYER_THRESHOLD) <= 0
                ? EPF_EMPLOYER_RATE_LOW : EPF_EMPLOYER_RATE_HIGH;
        BigDecimal epfEmployer = ceilRinggit(epfWage.multiply(employerRate));

        BigDecimal assumed = assumedWage(socsoEisWage);
        BigDecimal socsoEmployee = round2(assumed.multiply(SOCSO_EMPLOYEE_RATE));
        BigDecimal socsoEmployer = round2(assumed.multiply(SOCSO_EMPLOYER_RATE));
        BigDecimal eisEmployee = round2(assumed.multiply(EIS_RATE));
        BigDecimal eisEmployer = round2(assumed.multiply(EIS_RATE));

        return new Statutory(epfEmployee, epfEmployer, socsoEmployee, socsoEmployer, eisEmployee, eisEmployer);
    }

    /**
     * Compute a full payslip breakdown from period earnings.
     *
     * @param basic       monthly basic salary
     * @param overtimePay total overtime pay for the period (excluded from statutory bases)
     * @param claims      approved expense reimbursements for the period (not wages)
     * @param bonus       cash bonus for the period
     * @param profile     tax profile for the PCB estimate (null → treated as single)
     */
    public static Breakdown compute(BigDecimal basic, BigDecimal overtimePay, BigDecimal claims, BigDecimal bonus,
                                    TaxProfile profile) {
        BigDecimal b = round2(nz(basic));
        BigDecimal ot = round2(nz(overtimePay));
        BigDecimal cl = round2(nz(claims));
        BigDecimal bo = round2(nz(bonus));

        BigDecimal gross = b.add(ot).add(cl).add(bo);

        Statutory s = statutory(b, bo);
        BigDecimal pcb = monthlyPcb(b, s.epfEmployee(), profile);
        BigDecimal deductions = s.epfEmployee().add(s.socsoEmployee()).add(s.eisEmployee()).add(pcb);
        BigDecimal net = gross.subtract(deductions);

        return new Breakdown(b, ot, cl, bo, gross,
                s.epfEmployee(), s.socsoEmployee(), s.eisEmployee(), pcb, deductions, net);
    }

    /**
     * Estimated monthly PCB/MTD: annualise the basic salary, subtract the EPF relief (capped) and
     * personal reliefs, apply the YA2024 brackets, divide by 12. Returns 0 when no tax is due.
     */
    public static BigDecimal monthlyPcb(BigDecimal monthlyBasic, BigDecimal monthlyEpfEmployee, TaxProfile profile) {
        TaxProfile p = profile == null ? TaxProfile.SINGLE : profile;
        BigDecimal annualRemuneration = nz(monthlyBasic).multiply(MONTHS);
        BigDecimal annualEpfRelief = nz(monthlyEpfEmployee).multiply(MONTHS).min(EPF_RELIEF_CAP);

        BigDecimal reliefs = INDIVIDUAL_RELIEF;
        if (p.married() && !p.spouseWorking()) {
            reliefs = reliefs.add(SPOUSE_RELIEF);
        }
        int children = Math.max(0, p.children());
        reliefs = reliefs.add(CHILD_RELIEF.multiply(BigDecimal.valueOf(children)));

        BigDecimal chargeable = annualRemuneration.subtract(annualEpfRelief).subtract(reliefs);
        return annualIncomeTax(chargeable).divide(MONTHS, 2, RoundingMode.HALF_UP);
    }

    /** Annual resident-individual income tax on a chargeable income, via the YA2024 brackets. */
    static BigDecimal annualIncomeTax(BigDecimal chargeable) {
        if (chargeable == null || chargeable.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal tax = BigDecimal.ZERO;
        for (int i = 0; i < TAX_BAND_LOWER.length; i++) {
            BigDecimal lower = TAX_BAND_LOWER[i];
            if (chargeable.compareTo(lower) <= 0) {
                break;
            }
            BigDecimal upper = (i + 1 < TAX_BAND_LOWER.length) ? TAX_BAND_LOWER[i + 1] : null;
            BigDecimal top = (upper == null) ? chargeable : chargeable.min(upper);
            tax = tax.add(top.subtract(lower).multiply(TAX_BAND_RATE[i]));
        }
        return tax.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * The PERKESO wage-band assumed wage for SOCSO/EIS: the RM100 band midpoint,
     * capped at the RM6,000 ceiling (top band — wages over RM5,900 — assumed RM5,950).
     */
    static BigDecimal assumedWage(BigDecimal wage) {
        if (wage == null || wage.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        if (wage.compareTo(TOP_BAND_LOWER) > 0) {
            return TOP_BAND_MIDPOINT; // covers everything above RM5,900, incl. above the ceiling
        }
        // Upper bound of the RM100 band containing `wage`, then take the midpoint (−50).
        BigDecimal bandUpper = wage.divide(new BigDecimal("100"), 0, RoundingMode.CEILING)
                .multiply(new BigDecimal("100"));
        if (bandUpper.compareTo(new BigDecimal("100")) < 0) {
            bandUpper = new BigDecimal("100");
        }
        if (bandUpper.compareTo(CONTRIBUTION_CEILING) > 0) {
            bandUpper = CONTRIBUTION_CEILING;
        }
        return bandUpper.subtract(new BigDecimal("50"));
    }

    /** Round up to the next whole ringgit, at money scale (2dp). */
    private static BigDecimal ceilRinggit(BigDecimal v) {
        return v.setScale(0, RoundingMode.CEILING).setScale(2, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal round2(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
