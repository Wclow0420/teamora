import React, { useEffect, useState } from 'react';
import { View, Text } from 'react-native';
import { useRouter } from 'expo-router';
import { Screen } from '@/components/layout/Screen';
import { ScreenHeader, Button, TextField, SelectChips, type SelectOption } from '@/components/ui';
import { ReportingManagerField } from '@/components/ReportingManagerField';
import { WorkLocationField } from '@/components/WorkLocationField';
import { CompensationFields } from '@/components/CompensationFields';
import { StatutoryBankFields } from '@/components/StatutoryBankFields';
import { useCompanySettings, useCreateEmployee } from '@/api/queries';
import { ApiError } from '@/api/client';
import type { MaritalStatus, PayBasis, Role } from '@/api/types';
import { WEEKDAYS_MASK } from '@/lib/workweek';
import { palette, font } from '@/theme';

type NewRole = Extract<Role, 'EMPLOYEE' | 'MANAGER' | 'HR_ADMIN'>;

const ROLE_OPTIONS: SelectOption<NewRole>[] = [
  { value: 'EMPLOYEE', label: 'Employee' },
  { value: 'MANAGER', label: 'Manager' },
  { value: 'HR_ADMIN', label: 'HR Admin' },
];

const MARITAL_OPTIONS: SelectOption<MaritalStatus>[] = [
  { value: 'SINGLE', label: 'Single' },
  { value: 'MARRIED', label: 'Married' },
];

const SPOUSE_OPTIONS: SelectOption<'WORKING' | 'NOT_WORKING'>[] = [
  { value: 'NOT_WORKING', label: 'Not working' },
  { value: 'WORKING', label: 'Working' },
];

/** Parse a children-count input → a non-negative integer, or undefined if blank/invalid. */
function parseChildren(text: string): number | undefined {
  const trimmed = text.trim();
  if (!trimmed) return undefined;
  const n = Number(trimmed);
  return Number.isInteger(n) && n >= 0 ? n : undefined;
}

/** Parse a salary input → a non-negative number, or undefined if blank/invalid. */
function parseSalary(text: string): number | undefined {
  const trimmed = text.trim();
  if (!trimmed) return undefined;
  const n = Number(trimmed);
  return Number.isFinite(n) && n >= 0 ? n : undefined;
}

/** Parse an hours-per-day input → a positive number, or undefined if blank/invalid. */
function parseHours(text: string): number | undefined {
  const trimmed = text.trim();
  if (!trimmed) return undefined;
  const n = Number(trimmed);
  return Number.isFinite(n) && n > 0 ? n : undefined;
}

export default function EmployeeNew() {
  const router = useRouter();
  const create = useCreateEmployee();
  const settings = useCompanySettings();

  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<NewRole>('EMPLOYEE');
  const [jobTitle, setJobTitle] = useState('');
  const [department, setDepartment] = useState('');
  const [salary, setSalary] = useState('');
  const [reportingManagerId, setReportingManagerId] = useState<string | undefined>(undefined);
  const [workLocationId, setWorkLocationId] = useState<string | undefined>(undefined);
  const [maritalStatus, setMaritalStatus] = useState<MaritalStatus>('SINGLE');
  const [spouseWorking, setSpouseWorking] = useState<'WORKING' | 'NOT_WORKING'>('NOT_WORKING');
  const [children, setChildren] = useState('');
  // Statutory & bank identity (all optional).
  const [nric, setNric] = useState('');
  const [epfNo, setEpfNo] = useState('');
  const [socsoNo, setSocsoNo] = useState('');
  const [taxNo, setTaxNo] = useState('');
  const [bankName, setBankName] = useState('');
  const [bankAccountNo, setBankAccountNo] = useState('');
  const [error, setError] = useState<string | null>(null);

  // Compensation — seeded from the company defaults once they load (until touched).
  const [payBasis, setPayBasis] = useState<PayBasis>('MONTHLY');
  const [workingDays, setWorkingDays] = useState<number>(WEEKDAYS_MASK);
  const [hoursPerDay, setHoursPerDay] = useState('8');
  const [seeded, setSeeded] = useState(false);

  useEffect(() => {
    if (!seeded && settings.data) {
      setPayBasis(settings.data.defaultPayBasis);
      setWorkingDays(settings.data.defaultWorkingDays);
      setHoursPerDay(String(settings.data.defaultHoursPerDay));
      setSeeded(true);
    }
  }, [seeded, settings.data]);

  const onSubmit = async () => {
    setError(null);
    if (!fullName.trim() || !email.trim() || !password) {
      setError('Name, email and password are required.');
      return;
    }
    const monthlySalary = parseSalary(salary);
    if (salary.trim() && monthlySalary === undefined) {
      setError('Enter a valid monthly salary, or leave it blank.');
      return;
    }
    const numChildren = parseChildren(children);
    if (children.trim() && numChildren === undefined) {
      setError('Enter a valid number of children, or leave it blank.');
      return;
    }
    try {
      await create.mutateAsync({
        fullName,
        email: email.trim(),
        password,
        role,
        jobTitle: jobTitle.trim() || undefined,
        department: department.trim() || undefined,
        monthlySalary,
        reportingManagerId,
        workLocationId,
        maritalStatus,
        spouseWorking: maritalStatus === 'MARRIED' ? spouseWorking === 'WORKING' : undefined,
        numChildren,
        payBasis,
        workingDays,
        hoursPerDay: parseHours(hoursPerDay),
        nric: nric.trim() || undefined,
        epfNo: epfNo.trim() || undefined,
        socsoNo: socsoNo.trim() || undefined,
        taxNo: taxNo.trim() || undefined,
        bankName: bankName.trim() || undefined,
        bankAccountNo: bankAccountNo.trim() || undefined,
      });
      router.back();
    } catch (e) {
      if (e instanceof ApiError) setError(e.message);
      else setError('Something went wrong. Please try again.');
    }
  };

  return (
    <Screen>
      <ScreenHeader back title="Add employee" />

      <View style={{ gap: 14, marginTop: 8 }}>
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
        <TextField label="Job title (optional)" value={jobTitle} onChangeText={setJobTitle} />
        <TextField label="Department (optional)" value={department} onChangeText={setDepartment} />
        <TextField
          label="Monthly salary (RM)"
          value={salary}
          onChangeText={setSalary}
          keyboardType="decimal-pad"
          placeholder="e.g. 3500"
        />
        <ReportingManagerField value={reportingManagerId} onChange={setReportingManagerId} />
        <WorkLocationField value={workLocationId} onChange={setWorkLocationId} />

        <CompensationFields
          monthlySalary={parseSalary(salary) ?? null}
          payBasis={payBasis}
          onPayBasis={setPayBasis}
          workingDays={workingDays}
          onWorkingDays={setWorkingDays}
          hoursPerDay={hoursPerDay}
          onHoursPerDay={setHoursPerDay}
        />

        <View style={{ gap: 14, marginTop: 4 }}>
          <Text style={[font(700), { fontSize: 13, color: palette.ink }]}>Tax profile</Text>
          <Text style={[font(500), { fontSize: 11.5, color: palette.faint, marginTop: -8, lineHeight: 16 }]}>
            Used to estimate monthly income tax (PCB) on payslips.
          </Text>
          <SelectChips label="Marital status" options={MARITAL_OPTIONS} value={maritalStatus} onChange={setMaritalStatus} />
          {maritalStatus === 'MARRIED' && (
            <SelectChips label="Spouse" options={SPOUSE_OPTIONS} value={spouseWorking} onChange={setSpouseWorking} />
          )}
          <TextField
            label="Number of children"
            value={children}
            onChangeText={setChildren}
            keyboardType="number-pad"
            placeholder="e.g. 2"
          />
        </View>

        <StatutoryBankFields
          nric={nric}
          onNric={setNric}
          epfNo={epfNo}
          onEpfNo={setEpfNo}
          socsoNo={socsoNo}
          onSocsoNo={setSocsoNo}
          taxNo={taxNo}
          onTaxNo={setTaxNo}
          bankName={bankName}
          onBankName={setBankName}
          bankAccountNo={bankAccountNo}
          onBankAccountNo={setBankAccountNo}
        />

        {error && <Text style={{ color: palette.danger, fontSize: 12.5 }}>{error}</Text>}

        <Button
          label={create.isPending ? 'Adding…' : 'Add employee'}
          onPress={onSubmit}
          disabled={create.isPending}
        />
      </View>
    </Screen>
  );
}
