import React from 'react';
import { View, Text, Alert, type TextStyle } from 'react-native';
import { CollapsingHeaderScreen } from '@/components/layout/CollapsingHeaderScreen';
import { Button, Card, Chip, EmptyState, HeroCard, Icon, IconTile, type IconName } from '@/components/ui';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { usePayslips, useMe } from '@/api/queries';
import { sharePayslipPdf } from '@/lib/payslipPdf';
import type { Payslip } from '@/api/types';
import { formatDays } from '@/lib/numbers';
import { palette, font, radius, tint } from '@/theme';

/** Parse a money label like "4,285.50" → 4285.5. */
function num(label: string | null | undefined): number {
  const n = parseFloat(String(label ?? '').replace(/,/g, ''));
  return Number.isFinite(n) ? n : 0;
}
/** Format a number → "#,##0.00". */
function money(v: number): string {
  const [w, c] = Math.abs(v).toFixed(2).split('.');
  return `${v < 0 ? '-' : ''}${w.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${c}`;
}

const NUM: TextStyle = { fontVariant: ['tabular-nums'] };

type Earning = { icon: IconName; label: string; amount: string; color: string; bg: string };

/** Earnings shown: basic always; OT/claims/bonus only when non-zero (keeps the common month clean). */
function earnings(p: Payslip): Earning[] {
  const rows: Earning[] = [{ icon: 'briefcase', label: 'Basic salary', amount: p.basicLabel, color: palette.sage, bg: tint.sage }];
  if (num(p.overtimeLabel) !== 0) rows.push({ icon: 'clock', label: 'Overtime', amount: p.overtimeLabel, color: palette.amber, bg: tint.amber });
  if (num(p.claimsLabel) !== 0) rows.push({ icon: 'receipt', label: 'Claims reimbursed', amount: p.claimsLabel, color: palette.coral, bg: tint.coral });
  if (num(p.bonusLabel) !== 0) rows.push({ icon: 'gift', label: 'Performance bonus', amount: p.bonusLabel, color: palette.violet, bg: tint.violet });
  return rows;
}

function EyebrowRow({ children }: { children: string }) {
  return <Text style={[font(700), { fontSize: 11, letterSpacing: 1.1, textTransform: 'uppercase', color: palette.faint, marginTop: 22, marginBottom: 10, marginLeft: 4 }]}>{children}</Text>;
}

function SubtotalRow({ label, amount }: { label: string; amount: string }) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', paddingVertical: 14, paddingHorizontal: 16, backgroundColor: palette.surfaceSunken, borderTopWidth: 1, borderTopColor: palette.line }}>
      <Text style={[font(700), { flex: 1, fontSize: 14, color: palette.ink }]}>{label}</Text>
      <Text style={[font(800), { fontSize: 15, letterSpacing: -0.3, color: palette.ink }, NUM]}>{amount}</Text>
    </View>
  );
}

export default function Payroll() {
  const q = usePayslips();
  const me = useMe();
  const payslip = q.data?.[0];
  const [downloading, setDownloading] = React.useState(false);

  const onDownload = async (p: Payslip) => {
    setDownloading(true);
    try {
      await sharePayslipPdf(p, me.data ?? null);
    } catch {
      Alert.alert('Could not create PDF', 'Something went wrong preparing your payslip. Please try again.');
    } finally {
      setDownloading(false);
    }
  };

  return (
    <CollapsingHeaderScreen
      bottomInset={70}
      title="Payslip"
      accessory={<Chip label={payslip?.periodLabel ?? '—'} background={palette.surface} color={palette.soft} leading={<Icon name="chevD" size={14} color={palette.soft} />} />}
    >
      <AsyncBoundary loading={q.isLoading} error={q.error} onRetry={q.refetch}>
        {q.data && !payslip && (
          <EmptyState icon="wallet" title="No payslips yet" subtitle="Your payslips will appear here once payroll runs." tone={{ color: palette.coral, bg: tint.coral }} />
        )}
        {payslip && (
          <>
            <HeroCard
              eyebrow="Net pay · take-home"
              currency="RM"
              amount={payslip.netLabel}
              pill={{ label: `Paid ${payslip.payDateLabel} · ${payslip.bankLabel}`, dotColor: '#7FB894' }}
            />

            {/* earnings */}
            <EyebrowRow>Earnings</EyebrowRow>
            <Card padding={0} style={{ borderRadius: radius['2xl'], overflow: 'hidden' }}>
              {earnings(payslip).map((r, i) => (
                <View key={r.label} style={{ flexDirection: 'row', alignItems: 'center', gap: 13, paddingVertical: 14, paddingHorizontal: 16, borderTopWidth: i ? 1 : 0, borderTopColor: palette.line }}>
                  <IconTile icon={r.icon} color={r.color} background={r.bg} size={36} iconSize={19} cornerRadius={12} />
                  <Text style={[font(600), { flex: 1, fontSize: 14, letterSpacing: -0.1, color: palette.ink }]}>{r.label}</Text>
                  <Text style={[font(700), { fontSize: 14, letterSpacing: -0.2, color: palette.ink }, NUM]}>+ RM {r.amount}</Text>
                </View>
              ))}
              <SubtotalRow label="Gross pay" amount={`RM ${money(num(payslip.basicLabel) + num(payslip.overtimeLabel) + num(payslip.claimsLabel) + num(payslip.bonusLabel))}`} />
            </Card>

            {/* unpaid leave — informational; already reflected in the basic salary above */}
            {!!payslip.unpaidDays && payslip.unpaidDays > 0 && (
              <View style={{ flexDirection: 'row', gap: 11, marginTop: 12, padding: 14, borderRadius: radius.lg, backgroundColor: tint.amber }}>
                <IconTile icon="sun" color={palette.amber} background={palette.white} size={36} iconSize={19} cornerRadius={12} />
                <View style={{ flex: 1, minWidth: 0 }}>
                  <Text style={[font(700), { fontSize: 13, color: palette.ink }]}>
                    Unpaid leave · {payslip.unpaidDaysLabel ?? formatDays(payslip.unpaidDays)}
                  </Text>
                  <Text style={[font(500), { fontSize: 11.5, color: palette.soft, marginTop: 4, lineHeight: 16 }]}>
                    − RM {payslip.unpaidDeductionLabel ?? money(payslip.unpaidDeduction ?? 0)}
                    {payslip.dailyRateLabel ? ` · RM ${payslip.dailyRateLabel}/day` : ''} — already reflected in basic salary.
                  </Text>
                </View>
              </View>
            )}

            {/* deductions — tile-less, secondary */}
            <EyebrowRow>Deductions</EyebrowRow>
            <Card padding={0} style={{ borderRadius: radius['2xl'], overflow: 'hidden' }}>
              {[
                { label: 'EPF', amount: payslip.epfLabel },
                { label: 'SOCSO', amount: payslip.socsoLabel },
                { label: 'EIS', amount: payslip.eisLabel },
                { label: 'PCB (est.) · income tax', amount: payslip.pcbLabel },
              ].map((r, i) => (
                <View key={r.label} style={{ flexDirection: 'row', alignItems: 'center', paddingVertical: 12, paddingHorizontal: 16, borderTopWidth: i ? 1 : 0, borderTopColor: palette.line }}>
                  <Text style={[font(500), { flex: 1, fontSize: 13.5, letterSpacing: -0.1, color: palette.soft }]}>{r.label}</Text>
                  <Text style={[font(600), { fontSize: 13.5, color: palette.soft }, NUM]}>− RM {r.amount}</Text>
                </View>
              ))}
              <SubtotalRow label="Total deductions" amount={`− RM ${payslip.deductionsLabel}`} />
            </Card>

            <Button
              label={downloading ? 'Preparing…' : 'Download payslip (PDF)'}
              icon="download"
              style={{ marginTop: 18 }}
              disabled={downloading}
              onPress={() => onDownload(payslip)}
            />
            <Text style={[font(500), { fontSize: 11.5, color: palette.faint, marginTop: 12, lineHeight: 16, marginHorizontal: 2 }]}>
              PCB is an estimate — it doesn't include year-to-date tax already paid, so your final tax may differ.
            </Text>
          </>
        )}
      </AsyncBoundary>
    </CollapsingHeaderScreen>
  );
}
