package com.teamora.payroll.dto;

import com.teamora.employee.Employee;
import com.teamora.payroll.Payslip;
import com.teamora.payroll.PayslipFormat;
import com.teamora.payroll.PayslipStatus;

import java.math.BigDecimal;

/** Staff Payslip screen projection — pre-formatted breakdown for the mobile UI. */
public record PayslipResponse(
        java.util.UUID id,
        String period,
        String periodLabel,
        String netLabel,
        BigDecimal net,
        String basicLabel,
        String overtimeLabel,
        String claimsLabel,
        String bonusLabel,
        String deductionsLabel,
        String epfLabel,
        String socsoLabel,
        String eisLabel,
        String pcbLabel,
        BigDecimal unpaidDays,
        String unpaidDaysLabel,
        String unpaidDeductionLabel,
        String dailyRateLabel,
        String payDateLabel,
        PayslipStatus status,
        String statusLabel,
        String bankLabel
) {
    /**
     * @param owner the employee the payslip belongs to — the source of the real
     *              payout bank line (never a placeholder).
     */
    public static PayslipResponse from(Payslip p, Employee owner) {
        BigDecimal unpaidDays = p.getUnpaidDays() == null ? BigDecimal.ZERO : p.getUnpaidDays();
        String unpaidDaysLabel = unpaidDays.signum() == 0
                ? "None"
                : PayslipFormat.days(unpaidDays)
                        + (unpaidDays.compareTo(BigDecimal.ONE) == 0 ? " day" : " days");
        return new PayslipResponse(
                p.getId(),
                p.getPeriod(),
                PayslipFormat.periodLabel(p.getPeriod()),
                PayslipFormat.money(p.getNet()),
                p.getNet(),
                PayslipFormat.money(p.getBasic()),
                PayslipFormat.money(p.getOvertime()),
                PayslipFormat.money(p.getClaims()),
                PayslipFormat.money(p.getBonus()),
                PayslipFormat.money(p.getDeductions()),
                PayslipFormat.money(p.getEpf()),
                PayslipFormat.money(p.getSocso()),
                PayslipFormat.money(p.getEis()),
                PayslipFormat.money(p.getPcb()),
                unpaidDays,
                unpaidDaysLabel,
                PayslipFormat.money(p.getUnpaidDeduction()),
                p.getDailyRate() == null ? null : PayslipFormat.money(p.getDailyRate()),
                PayslipFormat.payDateLabel(p.getPayDate()),
                p.getStatus(),
                PayslipFormat.statusLabel(p.getStatus()),
                bankLabel(owner)
        );
    }

    /**
     * "Maybank ••1234" from the employee's real bank name + last 4 digits of the
     * account number; the bank name alone when there is no account number; and
     * {@code null} when neither is on record (the app then shows no bank line).
     */
    static String bankLabel(Employee e) {
        if (e == null) {
            return null;
        }
        String bank = trimToNull(e.getBankName());
        String account = trimToNull(e.getBankAccountNo());
        String tail = null;
        if (account != null) {
            String digits = account.replaceAll("[^0-9A-Za-z]", "");
            if (!digits.isEmpty()) {
                tail = "••" + digits.substring(Math.max(0, digits.length() - 4));
            }
        }
        if (bank == null) {
            return tail;
        }
        return tail == null ? bank : bank + " " + tail;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
