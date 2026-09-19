import type { EmployeeResponse, Payslip } from '@/api/types';

/**
 * Pure HTML builder for the payslip PDF document (no native deps, so it's testable
 * in isolation). The template hard-codes the "Warm & Human" brand colours — it's a
 * standalone document, not an RN component, so it can't pull from `@/theme`.
 */

// Brand palette (mirrors src/theme/colors.ts for the document medium).
const C = {
  bg: '#FBF5EE',
  surface: '#FFFFFF',
  ink: '#2C2620',
  soft: '#6B6258',
  faint: '#9B9186',
  line: '#EAE0D4',
  coral: '#EC6A4D',
  sage: '#5C9070',
};

function esc(value: string | null | undefined): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

/** Parse a money label like "4,285.50" → 4285.5. */
function num(label: string | null | undefined): number {
  const n = parseFloat(String(label ?? '').replace(/,/g, ''));
  return Number.isFinite(n) ? n : 0;
}

/** Format a number → "#,##0.00" without relying on Intl. */
function money(value: number): string {
  const fixed = Math.abs(value).toFixed(2);
  const [whole, cents] = fixed.split('.');
  const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return `${value < 0 ? '-' : ''}${grouped}.${cents}`;
}

function row(label: string, amount: string, opts: { sign?: '+' | '−'; muted?: boolean } = {}): string {
  const color = opts.muted ? C.soft : C.ink;
  const prefix = opts.sign ? `${opts.sign} ` : '';
  return `
    <tr>
      <td style="padding:9px 0;color:${C.soft};font-size:13px;">${esc(label)}</td>
      <td style="padding:9px 0;text-align:right;color:${color};font-size:13px;font-weight:600;white-space:nowrap;">
        ${prefix}RM ${esc(amount)}
      </td>
    </tr>`;
}

function sectionTitle(text: string): string {
  return `<div style="margin:22px 0 4px;font-size:11px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:${C.faint};">${esc(text)}</div>`;
}

/** Build the full payslip document HTML. */
export function payslipHtml(p: Payslip, employee: EmployeeResponse | null, generatedOn: Date): string {
  const company = employee?.companyName ?? 'Teamora';
  const name = employee?.fullName ?? '—';
  const staffId = employee?.staffId ? ` · ${esc(employee.staffId)}` : '';
  const generated = generatedOn.toISOString().slice(0, 10);
  const gross = money(num(p.basicLabel) + num(p.overtimeLabel) + num(p.claimsLabel) + num(p.bonusLabel));

  return `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <style>
    * { box-sizing: border-box; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
    body { margin: 0; font-family: -apple-system, 'Helvetica Neue', Arial, sans-serif; color: ${C.ink}; background: ${C.bg}; }
    .page { padding: 40px 38px; }
    table { width: 100%; border-collapse: collapse; }
    .divider { border-top: 1px solid ${C.line}; }
  </style>
</head>
<body>
  <div class="page">
    <!-- header -->
    <table>
      <tr>
        <td>
          <div style="font-size:20px;font-weight:800;color:${C.ink};">${esc(company)}</div>
          <div style="font-size:12px;color:${C.faint};margin-top:3px;">Payslip</div>
        </td>
        <td style="text-align:right;">
          <div style="font-size:13px;font-weight:700;color:${C.coral};">${esc(p.periodLabel)}</div>
          <div style="font-size:11px;color:${C.faint};margin-top:3px;">${esc(p.statusLabel)}</div>
        </td>
      </tr>
    </table>

    <div style="margin-top:18px;padding:14px 16px;background:${C.surface};border:1px solid ${C.line};border-radius:12px;">
      <table>
        <tr>
          <td>
            <div style="font-size:15px;font-weight:700;">${esc(name)}${staffId}</div>
            <div style="font-size:12px;color:${C.faint};margin-top:4px;">Pay date ${esc(p.payDateLabel)} · ${esc(p.bankLabel)}</div>
          </td>
          <td style="text-align:right;">
            <div style="font-size:11px;color:${C.faint};text-transform:uppercase;letter-spacing:0.5px;">Net pay</div>
            <div style="font-size:24px;font-weight:800;color:${C.ink};margin-top:2px;">RM ${esc(p.netLabel)}</div>
          </td>
        </tr>
      </table>
    </div>

    <!-- earnings -->
    ${sectionTitle('Earnings')}
    <table>
      ${row('Basic salary', p.basicLabel, { sign: '+' })}
      ${row('Overtime', p.overtimeLabel, { sign: '+' })}
      ${row('Claims reimbursed', p.claimsLabel, { sign: '+' })}
      ${row('Performance bonus', p.bonusLabel, { sign: '+' })}
      <tr class="divider"><td style="padding-top:9px;font-size:13px;font-weight:700;">Gross pay</td>
        <td style="padding-top:9px;text-align:right;font-size:13px;font-weight:800;">RM ${esc(gross)}</td></tr>
    </table>

    <!-- deductions -->
    ${sectionTitle('Deductions')}
    <table>
      ${row('EPF', p.epfLabel, { sign: '−', muted: true })}
      ${row('SOCSO', p.socsoLabel, { sign: '−', muted: true })}
      ${row('EIS', p.eisLabel, { sign: '−', muted: true })}
      ${row('PCB (est.) · income tax', p.pcbLabel, { sign: '−', muted: true })}
      <tr class="divider"><td style="padding-top:9px;font-size:13px;font-weight:700;">Total deductions</td>
        <td style="padding-top:9px;text-align:right;font-size:13px;font-weight:800;">− RM ${esc(p.deductionsLabel)}</td></tr>
    </table>

    <!-- net -->
    <div style="margin-top:22px;padding:16px 18px;background:${C.surface};border:1px solid ${C.line};border-radius:12px;">
      <table>
        <tr>
          <td style="font-size:14px;font-weight:700;">Net pay · take-home</td>
          <td style="text-align:right;font-size:20px;font-weight:800;color:${C.sage};">RM ${esc(p.netLabel)}</td>
        </tr>
      </table>
    </div>

    <!-- footer -->
    <div style="margin-top:26px;font-size:10.5px;line-height:1.5;color:${C.faint};">
      PCB is an <strong>estimate</strong> computed from your tax profile (YA2024 rates) and does not include
      year-to-date tax already paid, so your final tax may differ. This payslip is generated for your records
      and is not an official tax document. Generated by Teamora on ${esc(generated)}.
    </div>
  </div>
</body>
</html>`;
}
