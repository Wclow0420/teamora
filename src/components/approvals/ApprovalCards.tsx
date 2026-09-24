import React from 'react';
import { View, Text, Pressable } from 'react-native';
import { Avatar, Button, Card, Chip, Icon, type IconName } from '@/components/ui';
import { accentFromKey } from '@/api/accents';
import type { PendingClaim, PendingLeave, PendingOvertime } from '@/api/types';
import { palette, font, radius, tint } from '@/theme';

/** Shared approval cards used by both the manager inbox and the admin Approvals screen. */

/** Leave types are now company-configurable, so use one neutral leave icon. */
const LEAVE_ICON: IconName = 'sun';

type DecideProps = { pending: boolean; onApprove: () => void; onReject: () => void };

export function DecideRow({ pending, onApprove, onReject }: DecideProps) {
  return (
    <View style={{ flexDirection: 'row', gap: 10, marginTop: 14 }}>
      <Pressable
        disabled={pending}
        onPress={onReject}
        style={{ flex: 1, height: 44, borderRadius: 13, backgroundColor: palette.surface, borderWidth: 1, borderColor: palette.line, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 7, opacity: pending ? 0.5 : 1 }}
      >
        <Icon name="x" size={17} color={palette.danger} stroke={2.2} />
        <Text style={[font(700), { fontSize: 13.5, color: palette.danger }]}>Decline</Text>
      </Pressable>
      <Button label="Approve" variant="success" icon="check" height={44} style={{ flex: 1.4 }} disabled={pending} onPress={onApprove} />
    </View>
  );
}

export function LeaveApprovalCard({ item, pending, onApprove, onReject }: { item: PendingLeave } & DecideProps) {
  const accent = accentFromKey(item.accentColorKey);
  // Partial-day requests (half day / hours) are flagged so an approver never
  // mistakes a 2-hour slice for a full day off.
  const partial = item.durationUnit === 'HALF_DAY' || item.durationUnit === 'HOURS';
  return (
    <Card padding={16} style={{ borderRadius: radius['2xl'] }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
        <Avatar initial={item.initial} size={44} tint={{ bg: accent.bg, fg: accent.color }} />
        <View style={{ flex: 1, minWidth: 0 }}>
          <Text style={[font(700), { fontSize: 14, color: palette.ink }]} numberOfLines={1}>{item.employeeName}</Text>
          <Text style={[font(500), { fontSize: 11.5, color: palette.faint, marginTop: 5 }]}>{item.balanceLabel}</Text>
        </View>
        <Chip label={item.typeLabel} background={accent.bg} color={accent.color} leading={<Icon name={LEAVE_ICON} size={13} color={accent.color} />} />
      </View>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 13, paddingVertical: 10, paddingHorizontal: 13, borderRadius: 13, backgroundColor: palette.bg }}>
        <Icon name="calendar" size={16} color={palette.soft} />
        <Text style={[font(700), { flex: 1, fontSize: 12.5, color: palette.ink }]} numberOfLines={1}>
          {item.dateRangeLabel}
        </Text>
        {!!item.durationLabel && (
          <Chip
            label={item.durationLabel}
            size="sm"
            color={partial ? palette.amber : palette.soft}
            background={partial ? tint.amber : palette.surface}
          />
        )}
      </View>
      {item.reason && <Text style={[font(500), { fontSize: 12.5, lineHeight: 18, color: palette.soft, marginTop: 11 }]}>{item.reason}</Text>}
      <DecideRow pending={pending} onApprove={onApprove} onReject={onReject} />
    </Card>
  );
}

export function ClaimApprovalCard({ item, pending, onApprove, onReject }: { item: PendingClaim } & DecideProps) {
  const accent = accentFromKey(item.accentColorKey);
  return (
    <Card padding={16} style={{ borderRadius: radius['2xl'] }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
        <Avatar initial={item.initial} size={44} tint={{ bg: accent.bg, fg: accent.color }} />
        <View style={{ flex: 1, minWidth: 0 }}>
          <Text style={[font(700), { fontSize: 14, color: palette.ink }]} numberOfLines={1}>{item.employeeName}</Text>
          <Text style={[font(500), { fontSize: 11.5, color: palette.faint, marginTop: 5 }]} numberOfLines={1}>{item.title} · {item.claimDateLabel}</Text>
        </View>
        <Text style={[font(800), { fontSize: 15, color: palette.ink }]}>RM {item.amountLabel}</Text>
      </View>
      <DecideRow pending={pending} onApprove={onApprove} onReject={onReject} />
    </Card>
  );
}

export function OvertimeApprovalCard({ item, pending, onApprove, onReject }: { item: PendingOvertime } & DecideProps) {
  const accent = accentFromKey(item.accentColorKey);
  return (
    <Card padding={16} style={{ borderRadius: radius['2xl'] }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
        <Avatar initial={item.initial} size={44} tint={{ bg: accent.bg, fg: accent.color }} />
        <View style={{ flex: 1, minWidth: 0 }}>
          <Text style={[font(700), { fontSize: 14, color: palette.ink }]} numberOfLines={1}>{item.employeeName}</Text>
          <Text style={[font(500), { fontSize: 11.5, color: palette.faint, marginTop: 5 }]}>{item.workDateLabel}</Text>
        </View>
        <Chip label={item.hoursLabel} background={accent.bg} color={accent.color} leading={<Icon name="clock" size={13} color={accent.color} />} />
      </View>
      {item.reason && <Text style={[font(500), { fontSize: 12.5, lineHeight: 18, color: palette.soft, marginTop: 11 }]}>{item.reason}</Text>}
      <DecideRow pending={pending} onApprove={onApprove} onReject={onReject} />
    </Card>
  );
}

export function EmptyApprovals({ label }: { label: string }) {
  return (
    <Card style={{ alignItems: 'center', paddingVertical: 30 }}>
      <Icon name="check" size={28} color={palette.sage} />
      <Text style={[font(600), { fontSize: 13.5, color: palette.soft, marginTop: 10 }]}>{label}</Text>
      <Text style={[font(500), { fontSize: 12, color: palette.faint, marginTop: 4 }]}>You're all caught up.</Text>
    </Card>
  );
}

/** A pill tab row (used in headerExtra / inline). */
export function ApprovalTabs<T extends string>({
  tabs,
  value,
  onChange,
}: {
  tabs: { key: T; label: string; count: number }[];
  value: T;
  onChange: (key: T) => void;
}) {
  return (
    <View style={{ flexDirection: 'row', gap: 9 }}>
      {tabs.map((t) => {
        const on = t.key === value;
        return (
          <Pressable
            key={t.key}
            onPress={() => onChange(t.key)}
            style={{ flex: 1, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 7, paddingVertical: 10, borderRadius: 13, backgroundColor: on ? palette.ink : palette.surface, borderWidth: on ? 0 : 1, borderColor: palette.line }}
          >
            <Text style={[font(700), { fontSize: 13, color: on ? palette.white : palette.soft }]}>{t.label}</Text>
            <View style={{ minWidth: 19, height: 19, paddingHorizontal: 5, borderRadius: radius.pill, backgroundColor: on ? palette.coral : palette.bg, alignItems: 'center', justifyContent: 'center' }}>
              <Text style={[font(700), { fontSize: 10.5, color: on ? palette.white : palette.faint }]}>{t.count}</Text>
            </View>
          </Pressable>
        );
      })}
    </View>
  );
}
