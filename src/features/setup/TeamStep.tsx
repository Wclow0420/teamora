import React from 'react';
import { Pressable, Text, View } from 'react-native';
import Animated, { FadeIn, FadeOut, LinearTransition, useReducedMotion } from 'react-native-reanimated';
import { Avatar, Button, Card, Icon, SelectChips, TextField, type SelectOption } from '@/components/ui';
import { palette, radius, tint, type } from '@/theme';
import type { InviteRole, InviteRow } from './types';

const ROLE_OPTIONS: SelectOption<InviteRole>[] = [
  { value: 'EMPLOYEE', label: 'Employee' },
  { value: 'MANAGER', label: 'Manager' },
  { value: 'HR_ADMIN', label: 'HR Admin' },
];
const ROLE_LABEL: Record<InviteRole, string> = { EMPLOYEE: 'Employee', MANAGER: 'Manager', HR_ADMIN: 'HR Admin' };

type Props = {
  rows: InviteRow[];
  onChangeRow: (id: string, patch: Partial<Pick<InviteRow, 'fullName' | 'email' | 'role'>>) => void;
  onRemoveRow: (id: string) => void;
  onAddRow: () => void;
  onShare: (row: InviteRow) => void;
  /** Rows are being created — inputs lock. */
  busy: boolean;
};

/**
 * "Invite your team" — add people one card at a time. Once a person is created
 * their card turns into a summary with the temporary password and a "Share
 * login" button (native share sheet), since there is no invite email.
 */
export function TeamStep({ rows, onChangeRow, onRemoveRow, onAddRow, onShare, busy }: Props) {
  const reduce = useReducedMotion();
  const created = rows.filter((r) => r.status === 'created');
  const drafts = rows.filter((r) => r.status === 'draft');
  const layout = reduce ? undefined : LinearTransition.duration(220);

  return (
    <View style={{ gap: 12 }}>
      {created.map((r) => (
        <Animated.View key={r.id} entering={reduce ? undefined : FadeIn.duration(240)} layout={layout}>
          <Card padding={14} style={{ borderRadius: radius.xl }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
              <Avatar initial={r.fullName.trim().charAt(0).toUpperCase() || '·'} size={40} tint={{ bg: tint.coral, fg: palette.coral }} />
              <View style={{ flex: 1, minWidth: 0 }}>
                <Text style={[type.title, { color: palette.ink }]} numberOfLines={1}>
                  {r.fullName.trim()}
                </Text>
                <Text style={[type.meta, { color: palette.faint, marginTop: 2 }]} numberOfLines={1}>
                  {ROLE_LABEL[r.role]} · {r.email.trim()}
                </Text>
              </View>
              <View style={{ width: 24, height: 24, borderRadius: radius.pill, backgroundColor: tint.sage, alignItems: 'center', justifyContent: 'center' }}>
                <Icon name="check" size={14} color={palette.sage} stroke={2.8} />
              </View>
            </View>
            <View
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                gap: 10,
                marginTop: 12,
                paddingVertical: 10,
                paddingLeft: 12,
                paddingRight: 8,
                borderRadius: radius.md,
                backgroundColor: palette.surfaceSunken,
              }}
            >
              <View style={{ flex: 1, minWidth: 0 }}>
                <Text style={[type.micro, { color: palette.faint }]}>Temporary password</Text>
                {/* Never truncate: the owner has to read this out. Shrink to fit instead. */}
                <Text
                  selectable
                  style={[type.bodyStrong, { color: palette.ink, marginTop: 2 }]}
                  numberOfLines={1}
                  adjustsFontSizeToFit
                  minimumFontScale={0.7}
                >
                  {r.password}
                </Text>
              </View>
              <Button label="Share login" icon="export" variant="light" block={false} height={40} onPress={() => onShare(r)} />
            </View>
          </Card>
        </Animated.View>
      ))}

      {drafts.map((r, i) => (
        <Animated.View
          key={r.id}
          entering={reduce ? undefined : FadeIn.duration(240)}
          exiting={reduce ? undefined : FadeOut.duration(140)}
          layout={layout}
        >
          <Card padding={14} style={{ borderRadius: radius.xl, gap: 12, borderColor: r.error ? palette.danger : palette.line }}>
            {/* minHeight = the remove icon's, so the card doesn't shift when it appears */}
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', minHeight: 16 }}>
              <Text style={[type.eyebrow, { color: palette.faint }]}>Person {created.length + i + 1}</Text>
              {(drafts.length > 1 || created.length > 0) && (
                <Pressable
                  onPress={() => onRemoveRow(r.id)}
                  disabled={busy}
                  hitSlop={10}
                  accessibilityRole="button"
                  accessibilityLabel={`Remove person ${created.length + i + 1}`}
                >
                  <Icon name="x" size={16} color={palette.faint} />
                </Pressable>
              )}
            </View>
            <TextField
              label="Full name"
              value={r.fullName}
              onChangeText={(fullName) => onChangeRow(r.id, { fullName })}
              autoCapitalize="words"
              maxLength={100}
            />
            <TextField
              label="Work email"
              icon="mail"
              value={r.email}
              onChangeText={(email) => onChangeRow(r.id, { email })}
              keyboardType="email-address"
              autoCapitalize="none"
              maxLength={254}
            />
            <SelectChips label="Role" options={ROLE_OPTIONS} value={r.role} onChange={(role) => onChangeRow(r.id, { role })} />
            {!!r.error && <Text style={[type.meta, { fontSize: 12.5, lineHeight: 17, color: palette.danger }]}>{r.error}</Text>}
          </Card>
        </Animated.View>
      ))}

      <Animated.View layout={layout}>
        <Button label="Add another" icon="plus" variant="light" onPress={onAddRow} disabled={busy} />
        <Text style={[type.meta, { lineHeight: 18, color: palette.faint, marginTop: 12 }]}>
          We'll create a temporary password for each person. There's no invite email — share their login yourself, and
          they can change the password in Profile → Change password.
        </Text>
      </Animated.View>
    </View>
  );
}
