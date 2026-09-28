import React, { useState } from 'react';
import { View, Text } from 'react-native';
import { useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { ScreenHeader, Button, TextField, SelectChips, type SelectOption } from '@/components/ui';
import { useCreateEmployee } from '@/api/queries';
import { ApiError } from '@/api/client';
import type { Role } from '@/api/types';
import { palette, font, spacing } from '@/theme';

type NewRole = Extract<Role, 'EMPLOYEE' | 'MANAGER' | 'HR_ADMIN'>;

const ROLE_OPTIONS: SelectOption<NewRole>[] = [
  { value: 'EMPLOYEE', label: 'Employee' },
  { value: 'MANAGER', label: 'Manager' },
  { value: 'HR_ADMIN', label: 'HR Admin' },
];

/**
 * Add employee — deliberately short. Only what's needed to create the account
 * and let the person sign in; everything else (manager, work site, pay,
 * statutory, leave) is filled from the employee hub, which this replaces itself
 * with on success.
 */
export default function EmployeeNew() {
  const router = useRouter();
  const create = useCreateEmployee();

  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<NewRole>('EMPLOYEE');
  const [jobTitle, setJobTitle] = useState('');
  const [error, setError] = useState<string | null>(null);

  const onSubmit = async () => {
    if (create.isPending) return;
    setError(null);
    if (!fullName.trim() || !email.trim() || !password) {
      setError('Name, email and password are required.');
      return;
    }
    try {
      const created = await create.mutateAsync({
        fullName: fullName.trim(),
        email: email.trim(),
        password,
        role,
        jobTitle: jobTitle.trim() || undefined,
      });
      // Straight into the new employee's hub so the rest can be filled in from
      // the tiles — replace, so Back returns to the staff list, not this form.
      router.replace({
        pathname: '/admin/employee-edit',
        params: { id: created.id, name: created.fullName, email: created.email, role: created.role },
      });
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Something went wrong. Please try again.');
    }
  };

  return (
    <Screen>
      <ScreenHeader back title="Add employee" />

      <View style={{ gap: 14 }}>
        <Text style={[font(500), { fontSize: 12, color: palette.faint, lineHeight: 18 }]}>
          Just the essentials for now. Pay, statutory details, manager and leave come next, from the employee's
          own page.
        </Text>

        <TextField label="Full name" value={fullName} onChangeText={setFullName} autoCapitalize="words" />
        <TextField
          label="Work email"
          icon="mail"
          value={email}
          onChangeText={setEmail}
          keyboardType="email-address"
          autoCapitalize="none"
        />
        <TextField label="Temporary password" icon="lock" value={password} onChangeText={setPassword} secure />
        <SelectChips label="Role" options={ROLE_OPTIONS} value={role} onChange={setRole} />
        <TextField
          label="Job title (optional)"
          value={jobTitle}
          onChangeText={setJobTitle}
          placeholder="e.g. Sales Executive"
        />

        {error && <Text style={[font(600), { fontSize: 12.5, color: palette.danger }]}>{error}</Text>}

        <View style={{ marginTop: spacing.sm }}>
          <Button
            label={create.isPending ? 'Adding…' : 'Add employee'}
            icon="plus"
            onPress={onSubmit}
            disabled={create.isPending}
          />
        </View>
      </View>
    </Screen>
  );
}
