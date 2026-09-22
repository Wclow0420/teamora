package com.teamora.payroll.export;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * CP39 (LHDN monthly PCB/MTD) text generator — <b>best-effort, layout version "CP39-TXT-v1"</b>.
 *
 * <p><b>Layout-version assumption.</b> This produces a fixed-width text record set modelled on
 * the historically-published LHDN CP39 text-file structure (one detail line per employee, amounts
 * carried in <i>sen</i> with no decimal point). It is <b>NOT</b> guaranteed byte-exact against the
 * current official e-CP39 CSV/XML upload spec — that spec is deferred to v2 (see the build spec's
 * non-goals). The UI shows a "review against the latest LHDN format before submission" disclaimer,
 * and the PCB figure itself is the app's monthly <i>estimate</i> (no YTD accumulation), consistent
 * with the existing "PCB (est.)" labelling.
 *
 * <p><b>Fixed detail-record layout (1-based columns, 92 chars, space/zero padded):</b>
 * <pre>
 *   1–12   Income tax number (tax_no)        text,  left-justified, space-padded
 *   13–24  NRIC (digits only)                text,  left-justified, space-padded
 *   25–64  Employee name (uppercase ASCII)   text,  left-justified, space-padded
 *   65–75  PCB/MTD amount in sen             number,right-justified, zero-padded (11)
 *   76–86  CP38 amount in sen (always 0)     number,right-justified, zero-padded (11)
 *   87–88  Contribution month (MM)           number,zero-padded
 *   89–92  Contribution year (YYYY)          number
 * </pre>
 * A leading header line carries the employer name + contribution period. Lines are LF-separated.
 */
public final class Cp39Generator {

    private Cp39Generator() {
    }

    /** Layout version stamped in the header — bump on any field/width change. */
    public static final String LAYOUT_VERSION = "CP39-TXT-v1";

    private static final int W_TAX_NO = 12;
    private static final int W_NRIC = 12;
    private static final int W_NAME = 40;
    private static final int W_AMOUNT = 11;
    private static final char LF = '\n';

    /** One employee's PCB record for the CP39 file. */
    public record Record(String taxNo, String nric, String name, BigDecimal pcbAmount) {
    }

    /**
     * Build the full CP39 text document for a contribution period.
     *
     * @param employerName the paying company's name (header only)
     * @param period       {@code YYYY-MM}
     * @param records      one entry per employee with a PCB amount
     */
    public static String document(String employerName, String period, List<Record> records) {
        int[] my = parsePeriod(period);
        int month = my[0];
        int year = my[1];

        StringBuilder sb = new StringBuilder();
        sb.append("CP39|").append(LAYOUT_VERSION).append('|')
                .append(safe(employerName)).append('|')
                .append(pad2(month)).append('|').append(year).append(LF);
        for (Record r : records) {
            sb.append(employeeLine(r.taxNo(), r.nric(), r.name(), r.pcbAmount(), month, year)).append(LF);
        }
        return sb.toString();
    }

    /**
     * Build a single fixed-width CP39 detail line. Public + isolated so it can be unit-tested
     * against a known employee/amount ({@code Cp39Test}).
     */
    public static String employeeLine(String taxNo, String nric, String name,
                                      BigDecimal pcbAmount, int month, int year) {
        StringBuilder sb = new StringBuilder(92);
        sb.append(padRight(safe(taxNo), W_TAX_NO));
        sb.append(padRight(digitsOnly(nric), W_NRIC));
        sb.append(padRight(upperAscii(name), W_NAME));
        sb.append(padZero(toSen(pcbAmount), W_AMOUNT));   // PCB/MTD
        sb.append(padZero(0L, W_AMOUNT));                 // CP38 — not tracked here
        sb.append(pad2(month));
        sb.append(String.format("%04d", year));
        return sb.toString();
    }

    // ---- helpers ----

    /** RM → sen (× 100), rounded to the nearest sen. Null/blank → 0. */
    static long toSen(BigDecimal rm) {
        if (rm == null) {
            return 0L;
        }
        return rm.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static int[] parsePeriod(String period) {
        // Expects YYYY-MM; callers pass an already-validated period, but stay defensive.
        String[] parts = (period == null ? "" : period).split("-");
        if (parts.length != 2) {
            return new int[]{0, 0};
        }
        try {
            return new int[]{Integer.parseInt(parts[1]), Integer.parseInt(parts[0])};
        } catch (NumberFormatException e) {
            return new int[]{0, 0};
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String digitsOnly(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    private static String upperAscii(String s) {
        if (s == null) {
            return "";
        }
        return s.trim().toUpperCase(java.util.Locale.ENGLISH).replaceAll("[^\\x20-\\x7E]", "");
    }

    /** Left-justify + right-pad with spaces, truncating to width. */
    private static String padRight(String s, int width) {
        String v = s == null ? "" : s;
        if (v.length() >= width) {
            return v.substring(0, width);
        }
        return v + " ".repeat(width - v.length());
    }

    /** Right-justify a non-negative number with leading zeros, truncating overflow to the width. */
    private static String padZero(long value, int width) {
        String v = Long.toString(Math.max(0L, value));
        if (v.length() >= width) {
            return v.substring(v.length() - width);
        }
        return "0".repeat(width - v.length()) + v;
    }

    private static String pad2(int value) {
        return String.format("%02d", Math.max(0, value));
    }
}
