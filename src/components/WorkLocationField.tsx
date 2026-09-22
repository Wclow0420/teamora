import React from 'react';
import { useWorkLocations } from '@/api/queries';
import { SelectChips, type SelectOption } from '@/components/ui';

type Props = {
  /** Selected work-location id, or undefined for "None" (no geofence). */
  value?: string;
  onChange: (id: string | undefined) => void;
  /**
   * Display name of the currently-assigned site. Used so an assignment to a
   * now-inactive site (absent from the active list) still shows as selected.
   */
  currentName?: string;
};

/**
 * Picker for an employee's assigned work site (drives geofenced clock-in).
 * Options are the company's active sites plus a "None" sentinel — None clears
 * the assignment, so the employee can clock in from anywhere.
 */
export function WorkLocationField({ value, onChange, currentName }: Props) {
  const { data } = useWorkLocations();

  const active = data ?? [];
  const options: SelectOption<string>[] = [{ value: '', label: 'None' }];
  // Keep a selected-but-inactive site visible so it isn't silently cleared.
  if (value && !active.some((w) => w.id === value)) {
    options.push({ value, label: currentName ?? 'Current site' });
  }
  active.forEach((w) => options.push({ value: w.id, label: w.name }));

  return (
    <SelectChips
      label="Work location"
      options={options}
      value={value ?? ''}
      onChange={(v) => onChange(v || undefined)}
    />
  );
}
