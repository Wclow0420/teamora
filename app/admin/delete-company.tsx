import React, { useState } from 'react';
import { Alert, Text, View } from 'react-native';
import { useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import { Button, Card, Icon, PointList, ScreenHeader, SectionLabel, TextField, type Point } from '@/components/ui';
import { useAuth } from '@/hooks';
import { useCompany, useDeleteCompany, useMe } from '@/api/queries';
import { ApiError } from '@/api/client';
import { errorMessage } from '@/lib/errors';
import { palette, font, spacing, tint, type } from '@/theme';

const ERASED = { color: palette.danger, bg: tint.danger };

const WHAT_IS_ERASED: Point[] = [
  { icon: 'users', text: "Every staff account, including yours — nobody can sign in afterwards.", tone: ERASED },
  { icon: 'clock', text: 'All attendance records, including clock-in selfies and locations.', tone: ERASED },
  { icon: 'leave', text: 'All leave requests, balances and leave types.', tone: ERASED },
  { icon: 'receipt', text: 'All claims with their receipt photos, and all overtime records.', tone: ERASED },
  { icon: 'wallet', text: 'All payroll runs and payslips.', tone: ERASED },
  { icon: 'building', text: 'Company details and settings, work sites, shifts, the calendar and notifications.', tone: ERASED },
];

/** Case-insensitive, trimmed — the same rule the server applies. */
function namesMatch(typed: string, companyName: string): boolean {
  return typed.trim().toLowerCase() === companyName.trim().toLowerCase();
}

/**
 * Company settings → Danger zone → Delete company (OWNER only). Permanently
 * erases the company and every row that belongs to it — no grace period — then
 * signs the owner out. Guarded by typing the company name and the owner's
 * password, plus a final confirm.
 */
export default function DeleteCompany() {
  const me = useMe();
  const company = useCompany();

  return (
    <Screen>
      <ScreenHeader back title="Delete company" subtitle={company.data?.name} />
      <AsyncBoundary
        loading={me.isLoading || company.isLoading}
        error={me.error ?? company.error}
        onRetry={() => {
          void me.refetch();
          void company.refetch();
        }}
      >
        {me.data && company.data &&
          (me.data.role === 'OWNER' ? (
            <DeleteForm companyName={company.data.name} />
          ) : (
            <Text style={[type.body, { color: palette.soft }]}>
              Only the company owner can delete the company.
            </Text>
          ))}
      </AsyncBoundary>
    </Screen>
  );
}

function DeleteForm({ companyName }: { companyName: string }) {
  const router = useRouter();
  const { signOut } = useAuth();
  const remove = useDeleteCompany();

  const [confirmName, setConfirmName] = useState('');
  const [password, setPassword] = useState('');
  const [nameError, setNameError] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const nameOk = namesMatch(confirmName, companyName);
  const canDelete = nameOk && password.length > 0 && !remove.isPending;

  const run = async () => {
    setError(null);
    setNameError(null);
    setPasswordError(null);
    try {
      await remove.mutateAsync({ password, confirmName: confirmName.trim() });
    } catch (e) {
      // Put the server's sentence under the field it's about.
      if (e instanceof ApiError && /password/i.test(e.message)) setPasswordError(e.message);
      else if (e instanceof ApiError && /name/i.test(e.message)) setNameError(e.message);
      else setError(errorMessage(e, "Couldn't delete the company. Check your connection and try again."));
      return;
    }
    // Everything is gone server-side, including this account — end the session here.
    await signOut();
    router.replace('/onboarding');
    Alert.alert('Company deleted', `${companyName} and all of its data have been permanently erased from Teamora.`);
  };

  const onDelete = () => {
    if (!canDelete) return;
    Alert.alert(
      `Delete ${companyName}?`,
      "Every staff account and all attendance, leave, claims and payroll data will be erased straight away. This can't be undone.",
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Delete everything', style: 'destructive', onPress: () => void run() },
      ],
    );
  };

  return (
    <View style={{ gap: spacing.lg }}>
      <Text style={[type.body, { color: palette.soft }]}>
        This permanently erases {companyName} from Teamora. It happens straight away — there's no recovery period, and
        it can't be undone.
      </Text>

      <View>
        <SectionLabel title="What gets erased" />
        <PointList points={WHAT_IS_ERASED} />
      </View>

      <Card style={{ backgroundColor: tint.amber, borderColor: tint.amber, gap: spacing.md }} elevated={false}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing.sm }}>
          <Icon name="download" size={18} color={palette.ink} />
          <Text style={[type.title, { color: palette.ink, flex: 1 }]}>Export your payroll records first</Text>
        </View>
        <Text style={[type.body, { fontSize: 13.5, lineHeight: 20, color: palette.soft }]}>
          Malaysian law requires employers to keep payroll, EPF, SOCSO and tax records for several years (7 years for
          tax records). Teamora won't have a copy after this — download each month you need from Payroll → Reports
          &amp; export first.
        </Text>
        <Button label="Go to Payroll" variant="light" icon="wallet" onPress={() => router.push('/admin/payroll')} />
      </Card>

      <TextField
        label={`Type "${companyName}" to confirm`}
        value={confirmName}
        onChangeText={(t) => {
          setConfirmName(t);
          if (nameError) setNameError(null);
        }}
        autoCapitalize="none"
        placeholder={companyName}
        error={nameError}
      />
      <TextField
        label="Your password"
        icon="lock"
        value={password}
        onChangeText={(t) => {
          setPassword(t);
          if (passwordError) setPasswordError(null);
        }}
        secure
        autoCapitalize="none"
        error={passwordError}
      />

      {error && <Text style={[font(600), { fontSize: 12.5, lineHeight: 18, color: palette.danger }]}>{error}</Text>}

      <Button
        label={remove.isPending ? 'Deleting…' : 'Delete everything'}
        variant="danger"
        disabled={!canDelete}
        onPress={onDelete}
      />
      {(!nameOk || !password) && (
        <Text style={[font(500), { fontSize: 11.5, lineHeight: 16, color: palette.faint, marginTop: -spacing.sm, textAlign: 'center' }]}>
          Type the company name and your password to enable this.
        </Text>
      )}
    </View>
  );
}
