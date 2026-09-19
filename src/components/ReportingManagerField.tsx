import React from 'react';
import { useManagers } from '@/api/queries';
import { SelectChips, type SelectOption } from '@/components/ui';
import type { Role } from '@/api/types';

const ROLE_TAG: Record<Role, string> = { OWNER: 'Owner', HR_ADMIN: 'HR', MANAGER: 'Mgr', EMPLOYEE: '' };

type Props = {
  /** Selected manager id, or undefined for "None → owner". */
  value?: string;
  onChange: (id: string | undefined) => void;
};

/**
 * Picker for an employee's reporting manager (the approver of their requests).
 * Options come from `GET /api/employees/managers` (OWNER/HR_ADMIN/MANAGER), plus
 * a "None" option that defaults approval to the company owner.
 */
export function ReportingManagerField({ value, onChange }: Props) {
  const { data } = useManagers();

  const options: SelectOption<string>[] = [
    { value: '', label: 'None (Owner)' },
    ...(data ?? []).map((m) => ({ value: m.id, label: `${m.fullName} · ${ROLE_TAG[m.role]}` })),
  ];

  return (
    <SelectChips
      label="Reporting manager"
      options={options}
      value={value ?? ''}
      onChange={(v) => onChange(v || undefined)}
    />
  );
}
