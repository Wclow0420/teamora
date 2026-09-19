import React from 'react';
import { View, Text, Alert } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { CollapsingHeaderScreen } from '@/components/layout/CollapsingHeaderScreen';
import { Avatar, Button, Card, Chip, Icon, SectionLabel } from '@/components/ui';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import {
  useApprovePayrollRun,
  useMarkPayrollPaid,
  usePayrollRun,
  useRunPayroll,
} from '@/api/queries';
import type { PayrollRun, PayslipStatus } from '@/api/types';
import { palette, font, radius, gradients, tint } from '@/theme';

/** Current period as YYYY-MM, computed without a date library. */
function currentPeriod(): string {
  const now = new Date();
  const month = `${now.getMonth() + 1}`.padStart(2, '0');
  return `${now.getFullYear()}-${month}`;
}

function statusChipColors(status: PayslipStatus): { color: string; background: string } {
  switch (status) {
    case 'PAID':
      return { color: palette.sage, background: tint.sage };
    case 'APPROVED':
      return { color: palette.violet, background: tint.violet };
    default: // DRAFT / IN_REVIEW
      return { color: palette.amber, background: tint.amber };
  }
}

export default function Payroll() {
  const period = currentPeriod();
  const q = usePayrollRun(period);
  const run = useRunPayroll();
  const approve = useApprovePayrollRun();
  const markPaid = useMarkPayrollPaid();

  const data = q.data;
  const busy = run.isPending || approve.isPending || markPaid.isPending;

  const chip = data ? statusChipColors(data.status) : statusChipColors('DRAFT');

  const onRun = () => run.mutate(period);

  const onApprove = () => {
    Alert.alert(
      'Approve payroll?',
      `This locks ${data?.periodLabel ?? 'this run'} for payment. You can still mark it as paid afterwards.`,
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Approve', onPress: () => approve.mutate(period) },
      ],
    );
  };

  const onMarkPaid = () => {
    Alert.alert(
      'Mark as paid?',
      `Confirm that ${data?.netLabel ? `RM ${data.netLabel}` : 'this payroll'} has been disbursed to staff. This can't be undone.`,
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Mark as paid', onPress: () => markPaid.mutate(period) },
      ],
    );
  };

  return (
    <CollapsingHeaderScreen
      bottomInset={70}
      large
      title="Payroll"
      subtitle={data?.periodLabel ?? 'This month'}
      accessory={
        data?.generated ? (
          <Chip label={data.statusLabel} background={chip.background} color={chip.color} />
        ) : undefined
      }
    >
      <AsyncBoundary loading={q.isLoading} error={q.error} onRetry={q.refetch}>
        {data && (data.generated ? <RunView data={data} /> : <EmptyState busy={busy} onRun={onRun} />)}
        {data?.generated && (
          <Actions
            data={data}
            busy={busy}
            onApprove={onApprove}
            onMarkPaid={onMarkPaid}
            onReRun={onRun}
            running={run.isPending}
            approving={approve.isPending}
            paying={markPaid.isPending}
          />
        )}
      </AsyncBoundary>
    </CollapsingHeaderScreen>
  );
}

function EmptyState({ busy, onRun }: { busy: boolean; onRun: () => void }) {
  return (
    <Card style={{ alignItems: 'center', paddingVertical: 32, borderRadius: radius.xl }}>
      <View
        style={{
          width: 60,
          height: 60,
          borderRadius: radius.xl,
          backgroundColor: tint.coral,
          alignItems: 'center',
          justifyContent: 'center',
          marginBottom: 16,
        }}
      >
        <Icon name="wallet" size={28} color={palette.coral} />
      </View>
      <Text style={[font(700), { fontSize: 16, color: palette.ink }]}>No payroll run yet</Text>
      <Text style={[font(500), { fontSize: 13, color: palette.soft, textAlign: 'center', marginTop: 7, lineHeight: 19, paddingHorizontal: 12 }]}>
        Generate this month's draft payslips from staff salaries, approved overtime and claims.
      </Text>
      <Button
        label={busy ? 'Running…' : 'Run payroll'}
        icon="play"
        onPress={onRun}
        disabled={busy}
        style={{ marginTop: 20, alignSelf: 'stretch' }}
      />
    </Card>
  );
}

function RunView({ data }: { data: PayrollRun }) {
  return (
    <>
      {/* net hero */}
      <LinearGradient
        colors={gradients.cream}
        start={{ x: 0, y: 0 }}
        end={{ x: 0.4, y: 1 }}
        style={{
          borderRadius: radius['3xl'],
          paddingVertical: 22,
          paddingHorizontal: 20,
          borderWidth: 1,
          borderColor: '#F1D2B4',
          alignItems: 'center',
        }}
      >
        <Text style={[font(600), { fontSize: 12, color: palette.soft, letterSpacing: 0.5, textTransform: 'uppercase' }]}>
          Net disbursement
        </Text>
        <View style={{ flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'center', gap: 4, marginTop: 11 }}>
          <Text style={[font(700), { fontSize: 18, color: palette.ink }]}>RM</Text>
          <Text style={[font(800), { fontSize: 40, color: palette.ink, letterSpacing: -1 }]}>{data.netLabel}</Text>
        </View>
        <View style={{ flexDirection: 'row', gap: 8, marginTop: 11 }}>
          <View
            style={{
              flexDirection: 'row',
              alignItems: 'center',
              gap: 6,
              paddingVertical: 6,
              paddingHorizontal: 12,
              borderRadius: radius.pill,
              backgroundColor: palette.white,
            }}
          >
            <Icon name="users" size={14} color={palette.coral} />
            <Text style={[font(600), { fontSize: 11.5, color: palette.soft }]}>{data.employeeCount} employees</Text>
          </View>
          <View
            style={{
              flexDirection: 'row',
              alignItems: 'center',
              gap: 6,
              paddingVertical: 6,
              paddingHorizontal: 12,
              borderRadius: radius.pill,
              backgroundColor: palette.white,
            }}
          >
            <Icon name="calendar" size={14} color={palette.coral} />
            <Text style={[font(600), { fontSize: 11.5, color: palette.soft }]}>Pay {data.payDateLabel}</Text>
          </View>
        </View>
      </LinearGradient>

      {/* totals breakdown */}
      <Card padding={0} style={{ marginTop: 14, borderRadius: radius.xl, paddingHorizontal: 16 }}>
        {[
          { label: 'Gross salary', value: `RM ${data.grossLabel}` },
          { label: 'Statutory (EPF + SOCSO + EIS)', value: `RM ${data.statutoryLabel}` },
          { label: 'PCB (est.)', value: `RM ${data.pcbLabel}` },
          { label: 'Net disbursement', value: `RM ${data.netLabel}` },
        ].map((row, i) => (
          <View
            key={row.label}
            style={{
              flexDirection: 'row',
              alignItems: 'center',
              justifyContent: 'space-between',
              paddingVertical: 13,
              borderTopWidth: i > 0 ? 1 : 0,
              borderTopColor: palette.line,
            }}
          >
            <Text style={[font(600), { fontSize: 13, color: palette.soft }]}>{row.label}</Text>
            <Text style={[font(700), { fontSize: 13.5, color: palette.ink }]}>{row.value}</Text>
          </View>
        ))}
      </Card>

      {/* skipped note */}
      {data.skippedCount > 0 && (
        <View
          style={{
            flexDirection: 'row',
            alignItems: 'center',
            gap: 9,
            marginTop: 12,
            padding: 13,
            borderRadius: radius.lg,
            backgroundColor: tint.amber,
          }}
        >
          <Icon name="clock" size={17} color={palette.amber} />
          <Text style={[font(600), { flex: 1, fontSize: 12.5, color: palette.soft, lineHeight: 18 }]}>
            {data.skippedCount} {data.skippedCount === 1 ? 'employee' : 'employees'} skipped — no salary set. Add a monthly
            salary to include them.
          </Text>
        </View>
      )}

      {/* per-employee breakdown */}
      {data.lines.length > 0 && (
        <View style={{ marginTop: 20 }}>
          <SectionLabel title="Breakdown" action={`${data.lines.length} payslips`} actionColor={palette.faint} />
          <View style={{ gap: 9 }}>
            {data.lines.map((line) => (
              <Card key={line.payslipId} padding={0} elevated={false} style={{ borderRadius: radius.lg, padding: 14 }}>
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
                  <Avatar initial={line.initial} size={42} tint={{ bg: tint.neutral, fg: palette.soft }} />
                  <View style={{ flex: 1, minWidth: 0 }}>
                    <Text style={[font(700), { fontSize: 13.5, color: palette.ink }]} numberOfLines={1}>
                      {line.employeeName}
                    </Text>
                    {!!line.department && (
                      <Text style={[font(500), { fontSize: 11.5, color: palette.faint, marginTop: 4 }]} numberOfLines={1}>
                        {line.department}
                      </Text>
                    )}
                  </View>
                  <View style={{ alignItems: 'flex-end' }}>
                    <Text style={[font(800), { fontSize: 15, color: palette.ink }]}>RM {line.netLabel}</Text>
                    <Text style={[font(500), { fontSize: 10.5, color: palette.faint, marginTop: 3 }]}>net</Text>
                  </View>
                </View>
                <View
                  style={{
                    flexDirection: 'row',
                    flexWrap: 'wrap',
                    gap: 6,
                    marginTop: 11,
                    paddingTop: 11,
                    borderTopWidth: 1,
                    borderTopColor: palette.line,
                  }}
                >
                  {[
                    { k: 'Basic', v: line.basicLabel },
                    { k: 'OT', v: line.overtimeLabel },
                    { k: 'Claims', v: line.claimsLabel },
                    { k: 'Deductions', v: line.deductionsLabel },
                  ].map((m) => (
                    <View key={m.k} style={{ flexDirection: 'row', gap: 4 }}>
                      <Text style={[font(500), { fontSize: 11, color: palette.faint }]}>{m.k}</Text>
                      <Text style={[font(700), { fontSize: 11, color: palette.soft }]}>{m.v}</Text>
                    </View>
                  ))}
                </View>
              </Card>
            ))}
          </View>
        </View>
      )}
    </>
  );
}

function Actions({
  data,
  busy,
  onApprove,
  onMarkPaid,
  onReRun,
  running,
  approving,
  paying,
}: {
  data: PayrollRun;
  busy: boolean;
  onApprove: () => void;
  onMarkPaid: () => void;
  onReRun: () => void;
  running: boolean;
  approving: boolean;
  paying: boolean;
}) {
  if (data.status === 'PAID') {
    return (
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 9,
          marginTop: 18,
          paddingVertical: 15,
          borderRadius: radius.lg,
          backgroundColor: tint.sage,
        }}
      >
        <Icon name="check" size={18} color={palette.sage} stroke={2.6} />
        <Text style={[font(700), { fontSize: 14, color: palette.sage }]}>Paid · {data.payDateLabel}</Text>
      </View>
    );
  }

  if (data.status === 'APPROVED') {
    return (
      <Button
        label={paying ? 'Marking…' : 'Mark as paid'}
        variant="success"
        icon="check"
        height={54}
        disabled={busy}
        onPress={onMarkPaid}
        style={{ marginTop: 18 }}
      />
    );
  }

  // DRAFT / IN_REVIEW
  return (
    <View style={{ marginTop: 18, gap: 12 }}>
      <Button
        label={approving ? 'Approving…' : 'Approve payroll'}
        variant="dark"
        icon="check"
        height={54}
        disabled={busy}
        onPress={onApprove}
      />
      <Button
        label={running ? 'Re-running…' : 'Re-run'}
        variant="light"
        icon="play"
        disabled={busy}
        onPress={onReRun}
      />
    </View>
  );
}
