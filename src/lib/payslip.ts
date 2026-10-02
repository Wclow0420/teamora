import type { Payslip } from '@/api/types';

/**
 * Payslip presentation helpers (framework-agnostic), shared by the payslip
 * screen and the PDF so both describe a payslip's state the same, honest way.
 */

/** Staff only ever see final payslips — a draft / in-review run isn't one. */
export function isFinalPayslip(p: Payslip): boolean {
  const status = (p.status ?? '').toUpperCase();
  return status === 'APPROVED' || status === 'PAID';
}

export function isPaid(p: Payslip): boolean {
  return (p.status ?? '').toUpperCase() === 'PAID';
}

/**
 * One line saying where the money is: "Paid 28 Jun · Maybank ••1234" once it has
 * been paid, "Approved · pay date 28 Jun" while it's only approved. Parts with
 * no real value (no bank on file, no pay date) are left out — never "null".
 */
export function payslipStatusLine(p: Payslip): string {
  const payDate = p.payDateLabel?.trim() || null;
  const bank = p.bankLabel?.trim() || null;
  if (isPaid(p)) {
    return [payDate ? `Paid ${payDate}` : 'Paid', bank].filter(Boolean).join(' · ');
  }
  const status = p.statusLabel?.trim() || 'Approved';
  return [status, payDate ? `pay date ${payDate}` : null].filter(Boolean).join(' · ');
}
