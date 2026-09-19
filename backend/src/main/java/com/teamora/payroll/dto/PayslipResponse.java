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
        String payDateLabel,
        PayslipStatus status,
        String statusLabel,
        String bankLabel
) {
    public static PayslipResponse from(Payslip p) {
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
                PayslipFormat.payDateLabel(p.getPayDate()),
                p.getStatus(),
                PayslipFormat.statusLabel(p.getStatus()),
                // Placeholder bank line — real banking details aren't modelled yet.
                "Maybank ••4821"
        );
    }
}
