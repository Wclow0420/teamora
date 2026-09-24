package com.teamora.payroll;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/** Shared label formatting for payroll DTOs (grouped money, period labels, etc.). */
public final class PayslipFormat {

    private PayslipFormat() {
    }

    private static final DecimalFormat MONEY;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        symbols.setDecimalSeparator('.');
        MONEY = new DecimalFormat("#,##0.00", symbols);
    }

    /** Format money with thousands grouping and 2 decimals, e.g. "4,285.50". Null → "0.00". */
    public static String money(BigDecimal value) {
        return MONEY.format(value == null ? BigDecimal.ZERO : value);
    }

    /**
     * Format a (possibly fractional) day count without trailing zeros:
     * 2.00 → "2", 0.50 → "0.5". Null → "0".
     */
    public static String days(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return (stripped.scale() < 0 ? stripped.setScale(0) : stripped).toPlainString();
    }

    /** "2026-06" → "June 2026". Falls back to the raw period if it can't be parsed. */
    public static String periodLabel(String period) {
        if (period == null) {
            return "";
        }
        try {
            YearMonth ym = YearMonth.parse(period);
            return ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + ym.getYear();
        } catch (RuntimeException e) {
            return period;
        }
    }

    /** A pay date → "28 June" (day + full month). Null → "—". */
    public static String payDateLabel(LocalDate date) {
        if (date == null) {
            return "—";
        }
        return date.format(DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH));
    }

    /** Human label for a status, e.g. IN_REVIEW → "In review". */
    public static String statusLabel(PayslipStatus status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case DRAFT -> "Draft";
            case IN_REVIEW -> "In review";
            case APPROVED -> "Approved";
            case PAID -> "Paid";
        };
    }
}
