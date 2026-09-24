import React, { useEffect, useState } from 'react';
import { View, Text, Pressable } from 'react-native';
import { useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import {
  ScreenHeader,
  Button,
  TextField,
  SelectChips,
  WeekdayToggles,
  SectionLabel,
  Card,
  Chip,
  Icon,
  IconTile,
  type SelectOption,
} from '@/components/ui';
import { AsyncBoundary } from '@/components/layout/AsyncBoundary';
import {
  useAdminLeaveTypes,
  useCompany,
  useCompanySettings,
  useUpdateCompany,
  useUpdateCompanySettings,
} from '@/api/queries';
import { accentFromKey } from '@/api/accents';
import { ApiError } from '@/api/client';
import type { CompanyResponse, CompanySettings, LeaveTypeDef, PayBasis } from '@/api/types';
import { describeMask } from '@/lib/workweek';
import { palette, font, radius, tint } from '@/theme';

const PAY_BASIS_OPTIONS: SelectOption<PayBasis>[] = [
  { value: 'MONTHLY', label: 'Monthly' },
  { value: 'DAILY', label: 'Daily' },
  { value: 'HOURLY', label: 'Hourly' },
];

const MONTH_LABELS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'] as const;

/** Month chips 1–12 — SelectChips is string-valued, so the value is the month number as text. */
const LEAVE_YEAR_MONTH_OPTIONS: SelectOption<string>[] = MONTH_LABELS.map((label, i) => ({
  value: String(i + 1),
  label,
}));

/** Coerce a stored month to 1–12, defaulting to January. */
function safeMonth(value: number | undefined): number {
  if (value == null || !Number.isInteger(value) || value < 1 || value > 12) return 1;
  return value;
}

export default function CompanySettings() {
  const company = useCompany();
  const settings = useCompanySettings();
  const leaveTypes = useAdminLeaveTypes();

  return (
    <Screen>
      <ScreenHeader back title="Company settings" />

      <View style={{ marginTop: 4 }}>
        <SectionLabel title="Company profile" />
      </View>
      <AsyncBoundary loading={company.isLoading} error={company.error} onRetry={company.refetch}>
        {company.data && <CompanyForm company={company.data} />}
      </AsyncBoundary>

      <View style={{ marginTop: 24 }}>
        <SectionLabel title="Payroll & leave defaults" />
      </View>
      <Text style={[font(500), { fontSize: 11.5, color: palette.faint, marginBottom: 12, lineHeight: 16 }]}>
        Applied to new staff. Each employee can override these on their profile.
      </Text>
      <AsyncBoundary loading={settings.isLoading} error={settings.error} onRetry={settings.refetch}>
        {settings.data && <PayrollDefaultsForm settings={settings.data} />}
      </AsyncBoundary>

      <View style={{ marginTop: 24 }}>
        <SectionLabel title="Leave types" />
      </View>
      <Text style={[font(500), { fontSize: 11.5, color: palette.faint, marginBottom: 12, lineHeight: 16 }]}>
        Configure the leave types staff can apply for. Unpaid types deduct salary.
      </Text>
      <AsyncBoundary loading={leaveTypes.isLoading} error={leaveTypes.error} onRetry={leaveTypes.refetch}>
        {leaveTypes.data && <LeaveTypesManager types={leaveTypes.data} />}
      </AsyncBoundary>
    </Screen>
  );
}

function CompanyForm({ company }: { company: CompanyResponse }) {
  const router = useRouter();
  const update = useUpdateCompany();

  const [name, setName] = useState(company.name ?? '');
  const [registrationNo, setRegistrationNo] = useState(company.registrationNo ?? '');
  const [epfNo, setEpfNo] = useState(company.epfNo ?? '');
  const [socsoNo, setSocsoNo] = useState(company.socsoNo ?? '');
  const [email, setEmail] = useState(company.email ?? '');
  const [phone, setPhone] = useState(company.phone ?? '');
  const [address, setAddress] = useState(company.address ?? '');
  const [error, setError] = useState<string | null>(null);

  const onSubmit = async () => {
    setError(null);
    try {
      await update.mutateAsync({ name, registrationNo, epfNo, socsoNo, email, phone, address });
      router.back();
    } catch (e) {
      if (e instanceof ApiError) setError(e.message);
      else setError('Something went wrong. Please try again.');
    }
  };

  return (
    <View style={{ gap: 14, marginTop: 8 }}>
      <TextField label="Company name" value={name} onChangeText={setName} />
      <TextField label="Registration no. (SSM)" value={registrationNo} onChangeText={setRegistrationNo} />
      <TextField label="Employer EPF no." value={epfNo} onChangeText={setEpfNo} />
      <TextField label="Employer SOCSO no." value={socsoNo} onChangeText={setSocsoNo} />
      <TextField
        label="Email"
        value={email}
        onChangeText={setEmail}
        keyboardType="email-address"
        autoCapitalize="none"
      />
      <TextField label="Phone" value={phone} onChangeText={setPhone} keyboardType="phone-pad" />
      <TextField label="Address" value={address} onChangeText={setAddress} multiline />

      {error && <Text style={{ color: palette.danger, fontSize: 12.5 }}>{error}</Text>}

      <Button label={update.isPending ? 'Saving…' : 'Save changes'} onPress={onSubmit} disabled={update.isPending} />
    </View>
  );
}

function PayrollDefaultsForm({ settings }: { settings: CompanySettings }) {
  const update = useUpdateCompanySettings();

  const [payBasis, setPayBasis] = useState<PayBasis>(settings.defaultPayBasis);
  const [workingDays, setWorkingDays] = useState<number>(settings.defaultWorkingDays);
  const [hoursPerDay, setHoursPerDay] = useState(String(settings.defaultHoursPerDay));
  const [leaveYearStartMonth, setLeaveYearStartMonth] = useState(String(safeMonth(settings.leaveYearStartMonth)));
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const onSave = async () => {
    setError(null);
    setSaved(false);
    const hours = Number(hoursPerDay);
    if (!Number.isFinite(hours) || hours <= 0) {
      setError('Enter valid hours per day.');
      return;
    }
    try {
      await update.mutateAsync({
        defaultPayBasis: payBasis,
        defaultWorkingDays: workingDays,
        defaultHoursPerDay: hours,
        leaveYearStartMonth: Number(leaveYearStartMonth),
      });
      setSaved(true);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Something went wrong. Please try again.');
    }
  };

  return (
    <View style={{ gap: 14 }}>
      <SelectChips label="Default pay basis" options={PAY_BASIS_OPTIONS} value={payBasis} onChange={setPayBasis} />
      <WeekdayToggles label="Default working days" value={workingDays} onChange={setWorkingDays} />
      <Text style={[font(500), { fontSize: 11.5, color: palette.faint, marginTop: -8 }]}>{describeMask(workingDays)}</Text>
      <TextField
        label="Default hours per day"
        value={hoursPerDay}
        onChangeText={setHoursPerDay}
        keyboardType="decimal-pad"
        placeholder="e.g. 8"
      />
      <SelectChips
        label="Leave year starts"
        options={LEAVE_YEAR_MONTH_OPTIONS}
        value={leaveYearStartMonth}
        onChange={setLeaveYearStartMonth}
      />
      <Text style={[font(500), { fontSize: 11.5, color: palette.faint, marginTop: -8, lineHeight: 16 }]}>
        January = calendar year. Entitlement, accrual and carry-forward all follow this month.
      </Text>

      {error && <Text style={{ color: palette.danger, fontSize: 12.5 }}>{error}</Text>}
      {saved && !error && <Text style={{ color: palette.sage, fontSize: 12.5 }}>Defaults saved.</Text>}

      <Button
        label={update.isPending ? 'Saving…' : 'Save defaults'}
        onPress={onSave}
        disabled={update.isPending}
      />
    </View>
  );
}

function LeaveTypesManager({ types }: { types: LeaveTypeDef[] }) {
  const router = useRouter();

  const openEdit = (t: LeaveTypeDef) =>
    router.push({
      pathname: '/admin/leave-type-edit',
      params: {
        id: t.id,
        name: t.name,
        code: t.code,
        paid: String(t.paid),
        defaultEntitlementDays: String(t.defaultEntitlementDays),
        accrual: t.accrual,
        carryForwardMaxDays: String(t.carryForwardMaxDays ?? 0),
        colorKey: t.colorKey,
        active: String(t.active),
      },
    });

  return (
    <View style={{ gap: 10 }}>
      {types.length > 0 && (
        <Card padding={0} style={{ borderRadius: radius['2xl'], overflow: 'hidden' }}>
          {types.map((t, i) => {
            const accent = accentFromKey(t.colorKey);
            return (
              <Pressable
                key={t.id}
                onPress={() => openEdit(t)}
                style={{
                  flexDirection: 'row',
                  alignItems: 'center',
                  gap: 13,
                  paddingVertical: 13,
                  paddingHorizontal: 15,
                  borderTopWidth: i ? 1 : 0,
                  borderTopColor: palette.line,
                  opacity: t.active ? 1 : 0.55,
                }}
              >
                <IconTile icon="sun" color={accent.color} background={accent.bg} size={38} iconSize={19} cornerRadius={12} />
                <View style={{ flex: 1, minWidth: 0 }}>
                  <Text style={[font(700), { fontSize: 13.5, color: palette.ink }]} numberOfLines={1}>
                    {t.name}
                  </Text>
                  <Text style={[font(500), { fontSize: 11.5, color: palette.faint, marginTop: 4 }]}>
                    {t.defaultEntitlementDays} days/yr{t.active ? '' : ' · hidden'}
                  </Text>
                </View>
                <Chip
                  label={t.paid ? 'Paid' : 'Unpaid'}
                  size="sm"
                  color={t.paid ? palette.sage : palette.amber}
                  background={t.paid ? tint.sage : tint.amber}
                />
                <Icon name="chevR" size={16} color={palette.faint} />
              </Pressable>
            );
          })}
        </Card>
      )}

      <Button
        label="Add leave type"
        icon="plus"
        variant="light"
        onPress={() => router.push('/admin/leave-type-edit')}
      />
    </View>
  );
}
