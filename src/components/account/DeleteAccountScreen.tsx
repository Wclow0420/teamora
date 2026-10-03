import React, { useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { Button, Card, Icon, IconTile, PointList, ScreenHeader, TextField, type Point } from '@/components/ui';
import { useMe, useRequestAccountDeletion } from '@/api/queries';
import { errorMessage } from '@/lib/errors';
import { palette, font, spacing, tint, type } from '@/theme';

/** Server limit on the optional reason. */
const MAX_REASON = 300;

const POINTS: Point[] = [
  {
    icon: 'mail',
    text: 'This sends a request to your HR admin. They remove your account, and may get in touch with you first.',
    tone: { color: palette.coral, bg: tint.coral },
  },
  {
    icon: 'lock',
    text: "Once your account is removed, you can't sign in to Teamora with it any more.",
  },
  {
    icon: 'shield',
    text: 'Your employer keeps the payroll and statutory records the law requires them to keep — such as payslips, EPF, SOCSO and tax records — even after your account is removed.',
    tone: { color: palette.violet, bg: tint.violet },
  },
  {
    icon: 'user',
    text: 'Only need something corrected? Ask your HR admin instead — you can keep your account.',
  },
];

/** "3 Oct 2026" — falls back to the raw value if it isn't a parseable date. */
function formatRequestedAt(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString('en-MY', { day: 'numeric', month: 'short', year: 'numeric' });
}

/**
 * Profile → Delete my account, shared by the staff app and the admin app (for
 * HR admins). An employee can't erase their own records — the employer is the
 * data controller and must keep payroll/statutory records — so this asks the
 * company's HR admins to remove the account (Apple 5.1.1(v): deletion can be
 * started in the app). The OWNER deletes the whole company instead.
 */
export function DeleteAccountScreen() {
  const router = useRouter();
  const me = useMe();
  const request = useRequestAccountDeletion();

  const [reason, setReason] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [requestedAt, setRequestedAt] = useState<string | null>(null);

  const isOwner = me.data?.role === 'OWNER';

  const onSend = async () => {
    if (request.isPending) return;
    setError(null);
    try {
      const trimmed = reason.trim();
      const res = await request.mutateAsync(trimmed ? { reason: trimmed } : {});
      setRequestedAt(res?.requestedAt ?? new Date().toISOString());
    } catch (e) {
      setError(errorMessage(e, "Couldn't send your request. Check your connection and try again."));
    }
  };

  if (requestedAt) {
    return (
      <Screen>
        <ScreenHeader back title="Delete my account" />
        <Card style={{ alignItems: 'center', paddingVertical: 30, paddingHorizontal: 22 }}>
          <IconTile icon="check" color={palette.sage} background={tint.sage} size={56} iconSize={26} />
          <Text style={[type.h3, { color: palette.ink, marginTop: 14, textAlign: 'center' }]}>Request sent</Text>
          <Text style={[type.body, { color: palette.soft, marginTop: 8, textAlign: 'center' }]}>
            Your HR admin was notified on {formatRequestedAt(requestedAt)}. They'll remove your account — until
            then you can keep using Teamora as normal.
          </Text>
        </Card>
        <Button label="Done" variant="light" onPress={() => router.back()} style={{ marginTop: spacing.lg }} />
      </Screen>
    );
  }

  return (
    <Screen>
      <ScreenHeader back title="Delete my account" />

      {isOwner ? (
        <View style={{ gap: spacing.lg }}>
          <Text style={[type.body, { color: palette.soft }]}>
            As the owner, you can't remove just your own account. To close Teamora for your business, delete the
            company and all of its data from Company settings.
          </Text>
          <Button label="Open Company settings" variant="light" icon="building" onPress={() => router.replace('/admin/company-settings')} />
        </View>
      ) : (
        <View style={{ gap: spacing.lg }}>
          <Text style={[type.body, { color: palette.soft }]}>
            You can ask for your Teamora account to be removed. Here's what happens:
          </Text>

          <PointList points={POINTS} />

          <TextField
            label="Reason (optional)"
            value={reason}
            onChangeText={setReason}
            multiline
            maxLength={MAX_REASON}
            placeholder="e.g. I've left the company"
            helper={`${reason.length}/${MAX_REASON}`}
          />

          {error && <Text style={[font(600), { fontSize: 12.5, lineHeight: 18, color: palette.danger }]}>{error}</Text>}

          <Button
            label={request.isPending ? 'Sending…' : 'Send request'}
            variant="danger"
            icon="mail"
            disabled={request.isPending}
            onPress={onSend}
          />
        </View>
      )}

      <Pressable
        onPress={() => router.push('/privacy')}
        accessibilityRole="link"
        hitSlop={8}
        style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, marginTop: spacing['2xl'] }}
      >
        <Icon name="doc" size={15} color={palette.faint} />
        <Text style={[font(600), { fontSize: 12.5, lineHeight: 18, color: palette.soft }]}>How Teamora handles your data</Text>
      </Pressable>
    </Screen>
  );
}
