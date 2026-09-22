import React, { useState } from 'react';
import { View, Text, ScrollView } from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { Button, Card, Icon, ScreenHeader, SectionLabel } from '@/components/ui';
import { usePayrollExportSummary } from '@/api/queries';
import { payrollExportApi } from '@/api/endpoints';
import { ApiError } from '@/api/client';
import { shareExport } from '@/lib/exportFile';
import type { ExportType, StatutorySummary, StatutorySummaryRow, StatutorySummaryTotals } from '@/api/types';
import { palette, font, radius, tint } from '@/theme';

/** A money cell — right-aligned tabular numerals for clean column reading. */
function Money({ value, bold }: { value: string; bold?: boolean }) {
  return (
    <Text
      style={[
        font(bold ? 700 : 600),
        {
          fontSize: 12,
          color: bold ? palette.ink : palette.soft,
          fontVariant: ['tabular-nums'],
          textAlign: 'right',
        },
      ]}
      numberOfLines={1}
    >
      {value}
    </Text>
  );
}

const EXPORTS: { type: ExportType; label: string; sublabel: string; icon: 'doc' | 'wallet' | 'receipt' | 'shield' }[] = [
  { type: 'contributions', label: 'Contribution summary', sublabel: 'EPF, SOCSO, EIS & PCB per employee (CSV)', icon: 'doc' },
  { type: 'bank', label: 'Bank payment', sublabel: 'Names, accounts & net pay for payout (CSV)', icon: 'wallet' },
  { type: 'payroll', label: 'Full payroll', sublabel: 'Every line of the run (CSV)', icon: 'receipt' },
  { type: 'cp39', label: 'CP39 (PCB)', sublabel: 'LHDN monthly PCB text file', icon: 'shield' },
];

export default function PayrollExport() {
  const params = useLocalSearchParams<{ period?: string }>();
  const period = params.period ?? '';
  const q = usePayrollExportSummary(period);

  const [pending, setPending] = useState<ExportType | null>(null);
  const [error, setError] = useState<string | null>(null);

  const onExport = async (type: ExportType) => {
    if (pending) return;
    setError(null);
    setPending(type);
    try {
      const file = await payrollExportApi.file(period, type);
      await shareExport({
        filename: file.filename,
        mimeType: file.mimeType,
        content: file.content,
        dialogTitle: 'Payroll export',
      });
    } catch (e) {
      if (e instanceof ApiError) setError(e.message);
      else if (e instanceof Error) setError(e.message);
      else setError('Couldn’t generate that export. Please try again.');
    } finally {
      setPending(null);
    }
  };

  return (
    <Screen>
      <ScreenHeader back title="Reports & export" subtitle={q.data?.period ?? period} />

      <AsyncBoundary loading={q.isLoading} error={q.error} onRetry={q.refetch}>
        {q.data && <SummaryTable data={q.data} />}
      </AsyncBoundary>

      <View style={{ marginTop: 22 }}>
        <SectionLabel title="Export files" />
        <Text style={[font(500), { fontSize: 12, color: palette.faint, marginTop: -4, marginBottom: 12, lineHeight: 17 }]}>
          Generate a file for the period and share it — email it to your accountant, save to Files, or upload to a portal.
        </Text>

        <View style={{ gap: 10 }}>
          {EXPORTS.map((x) => (
            <View key={x.type}>
              <Card padding={0} elevated={false} style={{ borderRadius: radius.lg, padding: 14 }}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
                  <View
                    style={{
                      width: 40,
                      height: 40,
                      borderRadius: radius.md,
                      backgroundColor: tint.coral,
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}
                  >
                    <Icon name={x.icon} size={19} color={palette.coral} />
                  </View>
                  <View style={{ flex: 1, minWidth: 0 }}>
                    <Text style={[font(700), { fontSize: 13.5, color: palette.ink }]} numberOfLines={1}>
                      {x.label}
                    </Text>
                    <Text style={[font(500), { fontSize: 11.5, color: palette.faint, marginTop: 3 }]} numberOfLines={2}>
                      {x.sublabel}
                    </Text>
                  </View>
                  <Button
                    label={pending === x.type ? 'Preparing…' : 'Export'}
                    variant="light"
                    icon="download"
                    height={40}
                    disabled={pending !== null}
                    onPress={() => onExport(x.type)}
                  />
                </View>
              </Card>
              {x.type === 'cp39' && (
                <Text style={[font(500), { fontSize: 11, color: palette.faint, marginTop: 8, lineHeight: 16 }]}>
                  Best-effort LHDN CP39 — review against the latest format before submission.
                </Text>
              )}
            </View>
          ))}
        </View>

        {error && (
          <View
            style={{
              flexDirection: 'row',
              alignItems: 'center',
              gap: 9,
              marginTop: 14,
              padding: 13,
              borderRadius: radius.lg,
              backgroundColor: tint.danger,
            }}
          >
            <Icon name="x" size={16} color={palette.danger} />
            <Text style={[font(600), { flex: 1, fontSize: 12.5, color: palette.danger, lineHeight: 18 }]}>{error}</Text>
          </View>
        )}
      </View>
    </Screen>
  );
}

function SummaryTable({ data }: { data: StatutorySummary }) {
  if (data.rows.length === 0) {
    return (
      <Card style={{ alignItems: 'center', paddingVertical: 28, borderRadius: radius.xl }}>
        <View
          style={{
            width: 52,
            height: 52,
            borderRadius: radius.lg,
            backgroundColor: tint.neutral,
            alignItems: 'center',
            justifyContent: 'center',
            marginBottom: 12,
          }}
        >
          <Icon name="doc" size={24} color={palette.faint} />
        </View>
        <Text style={[font(700), { fontSize: 14.5, color: palette.ink }]}>No payslips to report</Text>
        <Text style={[font(500), { fontSize: 12.5, color: palette.soft, textAlign: 'center', marginTop: 6, lineHeight: 18, paddingHorizontal: 16 }]}>
          This period has no persisted payslips yet. Run payroll first, then come back to export.
        </Text>
      </Card>
    );
  }

  return (
    <View style={{ marginTop: 4 }}>
      <SectionLabel title="Statutory contributions" action={data.generatedAtLabel} actionColor={palette.faint} />
      <Card padding={0} style={{ borderRadius: radius.xl, paddingVertical: 4 }}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ paddingHorizontal: 14 }}>
          <View>
            <ContribHeader />
            {data.rows.map((row, i) => (
              <ContribRow key={`${row.staffId ?? row.employeeName}-${i}`} row={row} first={i === 0} />
            ))}
            <TotalsRow totals={data.totals} />
          </View>
        </ScrollView>
      </Card>
    </View>
  );
}

const COLS = {
  name: 150,
  money: 88,
} as const;

function HeaderCell({ label, width, right }: { label: string; width: number; right?: boolean }) {
  return (
    <Text
      style={[
        font(700),
        {
          width,
          fontSize: 10,
          letterSpacing: 0.4,
          textTransform: 'uppercase',
          color: palette.faint,
          textAlign: right ? 'right' : 'left',
        },
      ]}
      numberOfLines={1}
    >
      {label}
    </Text>
  );
}

function ContribHeader() {
  return (
    <View style={{ flexDirection: 'row', gap: 12, paddingVertical: 11 }}>
      <HeaderCell label="Employee" width={COLS.name} />
      <HeaderCell label="EPF (emp)" width={COLS.money} right />
      <HeaderCell label="EPF (co)" width={COLS.money} right />
      <HeaderCell label="SOCSO (emp)" width={COLS.money} right />
      <HeaderCell label="SOCSO (co)" width={COLS.money} right />
      <HeaderCell label="EIS (emp)" width={COLS.money} right />
      <HeaderCell label="EIS (co)" width={COLS.money} right />
      <HeaderCell label="PCB" width={COLS.money} right />
      <HeaderCell label="Net" width={COLS.money} right />
    </View>
  );
}

function ContribRow({ row, first }: { row: StatutorySummaryRow; first: boolean }) {
  return (
    <View
      style={{
        flexDirection: 'row',
        gap: 12,
        paddingVertical: 12,
        alignItems: 'center',
        borderTopWidth: first ? 0 : 1,
        borderTopColor: palette.line,
      }}
    >
      <View style={{ width: COLS.name }}>
        <Text style={[font(700), { fontSize: 12.5, color: palette.ink }]} numberOfLines={1}>
          {row.employeeName}
        </Text>
        <Text style={[font(500), { fontSize: 10.5, color: palette.faint, marginTop: 3 }]} numberOfLines={1}>
          {row.staffId ?? row.nric ?? '—'}
        </Text>
      </View>
      <View style={{ width: COLS.money }}><Money value={row.epfEmployeeLabel} /></View>
      <View style={{ width: COLS.money }}><Money value={row.epfEmployerLabel} /></View>
      <View style={{ width: COLS.money }}><Money value={row.socsoEmployeeLabel} /></View>
      <View style={{ width: COLS.money }}><Money value={row.socsoEmployerLabel} /></View>
      <View style={{ width: COLS.money }}><Money value={row.eisEmployeeLabel} /></View>
      <View style={{ width: COLS.money }}><Money value={row.eisEmployerLabel} /></View>
      <View style={{ width: COLS.money }}><Money value={row.pcbLabel} /></View>
      <View style={{ width: COLS.money }}><Money value={row.netLabel} bold /></View>
    </View>
  );
}

function TotalsRow({ totals }: { totals: StatutorySummaryTotals }) {
  return (
    <View
      style={{
        flexDirection: 'row',
        gap: 12,
        paddingVertical: 13,
        marginTop: 2,
        alignItems: 'center',
        borderRadius: radius.md,
        backgroundColor: palette.surfaceSunken,
        paddingHorizontal: 10,
        marginHorizontal: -10,
      }}
    >
      <View style={{ width: COLS.name }}>
        <Text style={[font(800), { fontSize: 12, color: palette.ink }]}>Company total</Text>
      </View>
      <View style={{ width: COLS.money }}><Money value={totals.epfEmployeeLabel} bold /></View>
      <View style={{ width: COLS.money }}><Money value={totals.epfEmployerLabel} bold /></View>
      <View style={{ width: COLS.money }}><Money value={totals.socsoEmployeeLabel} bold /></View>
      <View style={{ width: COLS.money }}><Money value={totals.socsoEmployerLabel} bold /></View>
      <View style={{ width: COLS.money }}><Money value={totals.eisEmployeeLabel} bold /></View>
      <View style={{ width: COLS.money }}><Money value={totals.eisEmployerLabel} bold /></View>
      <View style={{ width: COLS.money }}><Money value={totals.pcbLabel} bold /></View>
      <View style={{ width: COLS.money }}><Money value={totals.netLabel} bold /></View>
    </View>
  );
}
