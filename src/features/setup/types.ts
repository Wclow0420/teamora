import type { Role } from '@/api/types';

/** Which entry the wizard was opened from. */
export type SetupMode =
  /** Signed out: welcome → you → company (creates the account) → the rest. */
  | 'new'
  /** Signed-in owner picking up later: starts at the work week. */
  | 'resume';

export type AccountForm = { fullName: string; email: string; password: string };
export type AccountErrors = Partial<Record<keyof AccountForm, string>>;

export type CompanyForm = { companyName: string; registrationNo: string };
export type CompanyErrors = Partial<Record<keyof CompanyForm, string>>;

export type WorkWeekForm = {
  /** Working-weekday bitmask (bit0=Mon … bit6=Sun). */
  days: number;
  hours: number;
  start: Date;
  /** Minutes past the start time before a clock-in is Late. */
  grace: number;
};

export type WorkplaceChoice = 'site' | 'anywhere';
export type WorkplaceForm = {
  choice: WorkplaceChoice | null;
  name: string;
  coords: { latitude: number; longitude: number } | null;
  radiusM: number;
};
export type WorkplaceErrors = { name?: string; coords?: string };

export type InviteRole = Extract<Role, 'EMPLOYEE' | 'MANAGER' | 'HR_ADMIN'>;
export type InviteRow = {
  /** Local row id (not the employee id). */
  id: string;
  fullName: string;
  email: string;
  role: InviteRole;
  status: 'draft' | 'created';
  /** Why this row couldn't be added (validation or the server's sentence). */
  error?: string | null;
  /** The temporary password generated for a created row — shown so it can be shared. */
  password?: string;
};
