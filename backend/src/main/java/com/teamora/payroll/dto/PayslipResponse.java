package com.teamora.payroll.dto;

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
    public static PayslipResponse from(Payslip p) {
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
                // Placeholder bank line — real banking details aren't modelled yet.
                "Maybank ••4821"
        );
    }
}
