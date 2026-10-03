import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { attendanceApi, authApi, calendarAdminApi, calendarApi, claimApi, companyApi, companySettingsApi, dashboardApi, employeeApi, leaveApi, leaveBalanceAdminApi, leaveTypeApi, notificationApi, overtimeApi, payrollApi, payrollExportApi, scheduleApi, workLocationApi } from './endpoints';
import { getRefreshToken } from './tokenStore';
import type { AccountDeletionRequestBody, ApplyLeaveBody, DeleteCompanyBody, AssignShiftBody, ChangePasswordBody, ClockInBody, ResetPasswordWithCodeBody, CreateCompanyEventBody, CreateEmployeeBody, CreateLeaveTypeBody, CreateWorkLocationBody, OverrideLeaveEntitlementBody, Role, SubmitClaimBody, SubmitOvertimeBody, UpdateCompanyBody, UpdateCompanyEventBody, UpdateCompanySettingsBody, UpdateEmployeeBody, UpdateLeaveTypeBody, UpdateMyDetailsBody, UpdateWorkLocationBody, ImportHolidayItem } from './types';

/** Centralised query keys. */
export const qk = {
  me: ['me'] as const,
  today: ['attendance', 'today'] as const,
  attendance: (month?: string) => ['attendance', 'history', month ?? 'current'] as const,
  liveAttendance: ['attendance', 'live'] as const,
  leaveBalances: (year?: number) => ['leave', 'balances', year ?? 'current'] as const,
  adminLeaveBalances: (employeeId: string, year?: number) =>
    ['leave', 'balances', 'admin', employeeId, year ?? 'current'] as const,
  leaveRequests: ['leave', 'requests'] as const,
  pendingLeave: ['leave', 'pending'] as const,
  claims: ['claims', 'me'] as const,
  pendingClaims: ['claims', 'pending'] as const,
  payslips: ['payroll', 'payslips'] as const,
  payslip: (period: string) => ['payroll', 'payslip', period] as const,
  payrollSummary: (period?: string) => ['payroll', 'summary', period ?? 'latest'] as const,
  payrollRun: (period: string) => ['payroll', 'run', period] as const,
  payrollExportSummary: (period: string) => ['payroll', 'export', 'summary', period] as const,
  staff: (dept?: string, q?: string) => ['employees', dept ?? 'all', q ?? ''] as const,
  company: ['company', 'me'] as const,
  companySettings: ['companySettings'] as const,
  leaveTypes: ['leaveTypes', 'active'] as const,
  adminLeaveTypes: ['leaveTypes', 'all'] as const,
  workLocations: ['workLocations', 'active'] as const,
  adminWorkLocations: ['workLocations', 'all'] as const,
  adminCalendar: (month?: string) => ['adminCalendar', month ?? 'current'] as const,
  holidaySuggestions: (year?: number) => ['adminCalendar', 'holidays', year ?? 'default'] as const,
};

// ---- Profile ----
export const useMe = () => useQuery({ queryKey: qk.me, queryFn: employeeApi.me });

/** Self-service profile edit (phone only). Refreshes `me` so every screen sees it. */
export function useUpdateMyDetails() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateMyDetailsBody) => employeeApi.updateMe(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.me }),
  });
}

// ---- Passwords ----
/**
 * Change my own password. The server verifies the current one (400 if wrong).
 *
 * A password change revokes the account's refresh tokens (signing out other
 * devices). We pass this device's own refresh token so the server spares that
 * one session — the user stays signed in here.
 */
export function useChangePassword() {
  return useMutation({
    mutationFn: (body: Pick<ChangePasswordBody, 'currentPassword' | 'newPassword'>) =>
      authApi.changePassword({ ...body, refreshToken: getRefreshToken() ?? undefined }),
  });
}
/** Signed-out: ask for a one-time password reset code. */
export function useForgotPassword() {
  return useMutation({ mutationFn: (email: string) => authApi.forgotPassword({ email }) });
}
/** Signed-out: redeem the code and set a new password. */
export function useResetPasswordWithCode() {
  return useMutation({ mutationFn: (body: ResetPasswordWithCodeBody) => authApi.resetPassword(body) });
}
/** OWNER/HR_ADMIN: set a temporary password for a team member. */
export function useResetEmployeePassword() {
  return useMutation({
    mutationFn: ({ id, newPassword }: { id: string; newPassword: string }) =>
      employeeApi.resetPassword(id, { newPassword }),
  });
}

// ---- Attendance ----
export const useTodayAttendance = () => useQuery({ queryKey: qk.today, queryFn: attendanceApi.today });
export const useAttendanceHistory = (month?: string) =>
  useQuery({ queryKey: qk.attendance(month), queryFn: () => attendanceApi.history(month) });
export const useLiveAttendance = () =>
  useQuery({ queryKey: qk.liveAttendance, queryFn: attendanceApi.live, refetchInterval: 30_000 });

export function useClockIn() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body?: ClockInBody) => attendanceApi.clockIn(body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['attendance'] });
    },
  });
}
export function useClockOut() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: attendanceApi.clockOut,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['attendance'] });
    },
  });
}

// ---- Leave ----
/** My leave balances. `year` names a leave year by its starting calendar year. */
export const useLeaveBalances = (year?: number) =>
  useQuery({ queryKey: qk.leaveBalances(year), queryFn: () => leaveApi.balances(year) });
export const useLeaveRequests = () => useQuery({ queryKey: qk.leaveRequests, queryFn: leaveApi.requests });
export const usePendingLeave = () => useQuery({ queryKey: qk.pendingLeave, queryFn: leaveApi.pending });

// ---- Leave types (config) ----
/** Active leave types for staff pickers. */
export const useLeaveTypes = () => useQuery({ queryKey: qk.leaveTypes, queryFn: leaveTypeApi.active });
/** All leave types (incl. inactive) for admin management. */
export const useAdminLeaveTypes = () => useQuery({ queryKey: qk.adminLeaveTypes, queryFn: leaveTypeApi.all });

export function useCreateLeaveType() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateLeaveTypeBody) => leaveTypeApi.create(body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['leaveTypes'] });
      qc.invalidateQueries({ queryKey: ['leave'] });
    },
  });
}
export function useUpdateLeaveType() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateLeaveTypeBody }) => leaveTypeApi.update(id, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['leaveTypes'] });
      qc.invalidateQueries({ queryKey: ['leave'] });
    },
  });
}

// ---- Work locations (geofencing) ----
/** Active work sites for staff pickers / clock-in. */
export const useWorkLocations = () =>
  useQuery({ queryKey: qk.workLocations, queryFn: workLocationApi.list });
/** All work sites (incl. inactive) for admin management. */
export const useAdminWorkLocations = () =>
  useQuery({ queryKey: qk.adminWorkLocations, queryFn: workLocationApi.adminList });

export function useCreateWorkLocation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateWorkLocationBody) => workLocationApi.create(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['workLocations'] }),
  });
}
export function useUpdateWorkLocation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateWorkLocationBody }) =>
      workLocationApi.update(id, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['workLocations'] }),
  });
}

// ---- Leave balances (admin: entitlement overrides) ----
/** One employee's balances for a leave year — powers the admin entitlement editor. */
export const useAdminLeaveBalances = (employeeId: string | undefined, year?: number) => {
  const id = employeeId ?? '';
  return useQuery({
    queryKey: qk.adminLeaveBalances(id, year),
    queryFn: () => leaveBalanceAdminApi.list(id, year),
    enabled: id.length > 0,
  });
};

/** Override an employee's entitlement for one leave type / year. */
export function useOverrideLeaveEntitlement() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: OverrideLeaveEntitlementBody) => leaveBalanceAdminApi.override(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['leave'] }),
  });
}

export function useApplyLeave() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: ApplyLeaveBody) => leaveApi.apply(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['leave'] }),
  });
}
export function useDecideLeave() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, decision, reason }: { id: string; decision: 'approve' | 'reject'; reason?: string }) =>
      decision === 'approve' ? leaveApi.approve(id) : leaveApi.reject(id, reason),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['leave'] }),
  });
}

// ---- Claims ----
export const useClaims = () => useQuery({ queryKey: qk.claims, queryFn: claimApi.summary });
export const usePendingClaims = () => useQuery({ queryKey: qk.pendingClaims, queryFn: claimApi.pending });

export function useSubmitClaim() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: SubmitClaimBody) => claimApi.submit(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['claims'] }),
  });
}
export function useDecideClaim() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, decision, reason }: { id: string; decision: 'approve' | 'reject'; reason?: string }) =>
      decision === 'approve' ? claimApi.approve(id) : claimApi.reject(id, reason),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['claims'] }),
  });
}

// ---- Overtime ----
export const useOvertime = () => useQuery({ queryKey: ['overtime', 'me'], queryFn: overtimeApi.mine });
export const usePendingOvertime = () => useQuery({ queryKey: ['overtime', 'pending'], queryFn: overtimeApi.pending });

export function useSubmitOvertime() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: SubmitOvertimeBody) => overtimeApi.submit(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['overtime'] }),
  });
}
export function useDecideOvertime() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, decision, reason }: { id: string; decision: 'approve' | 'reject'; reason?: string }) =>
      decision === 'approve' ? overtimeApi.approve(id) : overtimeApi.reject(id, reason),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['overtime'] }),
  });
}

// ---- Dashboard (admin) ----
export const useDashboard = () =>
  useQuery({ queryKey: ['dashboard'], queryFn: dashboardApi.summary, refetchInterval: 60_000 });

// ---- Payroll ----
export const usePayslips = () => useQuery({ queryKey: qk.payslips, queryFn: payrollApi.payslips });
export const usePayslip = (period: string) =>
  useQuery({ queryKey: qk.payslip(period), queryFn: () => payrollApi.payslip(period), enabled: !!period });
export const usePayrollSummary = (period?: string) =>
  useQuery({ queryKey: qk.payrollSummary(period), queryFn: () => payrollApi.summary(period) });

export const usePayrollRun = (period: string) =>
  useQuery({ queryKey: qk.payrollRun(period), queryFn: () => payrollApi.runView(period), enabled: !!period });

function useInvalidatePayroll() {
  const qc = useQueryClient();
  return () => {
    qc.invalidateQueries({ queryKey: ['payroll'] });
    qc.invalidateQueries({ queryKey: ['dashboard'] });
  };
}

export function useRunPayroll() {
  const invalidate = useInvalidatePayroll();
  return useMutation({ mutationFn: (period: string) => payrollApi.run(period), onSuccess: invalidate });
}
export function useApprovePayrollRun() {
  const invalidate = useInvalidatePayroll();
  return useMutation({ mutationFn: (period: string) => payrollApi.approveRun(period), onSuccess: invalidate });
}
export function useMarkPayrollPaid() {
  const invalidate = useInvalidatePayroll();
  return useMutation({ mutationFn: (period: string) => payrollApi.markPaid(period), onSuccess: invalidate });
}

// ---- Payroll statutory export ----
/** On-screen statutory contribution summary for a period (enabled once a period is set). */
export const usePayrollExportSummary = (period: string) =>
  useQuery({
    queryKey: qk.payrollExportSummary(period),
    queryFn: () => payrollExportApi.summary(period),
    enabled: !!period,
  });

// ---- Calendar ----
export const useCalendar = (month?: string) =>
  useQuery({ queryKey: ['calendar', month ?? 'current'], queryFn: () => calendarApi.month(month) });

// ---- Company calendar (admin CRUD) ----
/** All company events in `month` (YYYY-MM); omit for the current month. */
export const useAdminCalendarEvents = (month?: string) =>
  useQuery({ queryKey: qk.adminCalendar(month), queryFn: () => calendarAdminApi.list(month) });

/**
 * Both the admin list and the staff calendar are invalidated on every mutation —
 * a new holiday must show up on employees' calendars immediately.
 */
function invalidateCalendars(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: ['adminCalendar'] });
  qc.invalidateQueries({ queryKey: ['calendar'] });
}

export function useCreateCompanyEvent() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateCompanyEventBody) => calendarAdminApi.create(body),
    onSuccess: () => invalidateCalendars(qc),
  });
}
export function useUpdateCompanyEvent() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateCompanyEventBody }) => calendarAdminApi.update(id, body),
    onSuccess: () => invalidateCalendars(qc),
  });
}
/**
 * Suggested Malaysian public holidays for `year`. Keyed under `adminCalendar`
 * so any calendar mutation refreshes the "Added" flags too.
 */
export const useHolidaySuggestions = (year?: number) =>
  useQuery({ queryKey: qk.holidaySuggestions(year), queryFn: () => calendarAdminApi.holidaySuggestions(year) });

export function useImportHolidays() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (items: ImportHolidayItem[]) => calendarAdminApi.importHolidays(items),
    onSuccess: () => invalidateCalendars(qc),
  });
}
export function useDeleteCompanyEvent() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => calendarAdminApi.remove(id),
    onSuccess: () => invalidateCalendars(qc),
  });
}

// ---- Notifications ----
export const useNotifications = () => useQuery({ queryKey: ['notifications'], queryFn: notificationApi.list });
export function useMarkAllRead() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => notificationApi.readAll(),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  });
}

// ---- Scheduling (admin) ----
export const useSchedule = (weekStart?: string) =>
  useQuery({ queryKey: ['schedule', weekStart ?? 'current'], queryFn: () => scheduleApi.week(weekStart) });
export function useAssignShift() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: AssignShiftBody) => scheduleApi.assign(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['schedule'] }),
  });
}

// ---- Employees (admin) ----
export const useStaff = (dept?: string, q?: string) =>
  useQuery({ queryKey: qk.staff(dept, q), queryFn: () => employeeApi.list(dept, q) });

/** Full employee detail (incl. effective compensation + derived rates). */
export const useEmployee = (id?: string) =>
  useQuery({ queryKey: ['employees', 'detail', id], queryFn: () => employeeApi.get(id!), enabled: !!id });

export function useCreateEmployee() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateEmployeeBody) => employeeApi.create(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['employees'] }),
  });
}
export function useChangeRole() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, role }: { id: string; role: Role }) => employeeApi.changeRole(id, { role }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['employees'] }),
  });
}
/**
 * Partial employee update. The `['employees']` prefix invalidates the staff list
 * AND the detail query (`['employees', 'detail', id]`) — React Query matches by
 * key prefix — so the employee hub's tile summaries refresh as soon as one of
 * its category screens saves and pops back.
 */
export function useUpdateEmployee() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateEmployeeBody }) => employeeApi.update(id, body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['employees'] }),
  });
}
export const useManagers = () => useQuery({ queryKey: ['employees', 'managers'], queryFn: employeeApi.managers });
export function useTransferOwnership() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => employeeApi.transferOwnership(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['employees'] });
      qc.invalidateQueries({ queryKey: qk.me });
    },
  });
}

/**
 * Deactivate / reactivate a team member. Their record (and payroll history)
 * stays; only their access changes — so refresh the directory, their detail,
 * the manager pickers (a deactivated manager drops out) and the dashboard headcount.
 */
export function useDeactivateEmployee() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => employeeApi.deactivate(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['employees'] });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}
export function useReactivateEmployee() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => employeeApi.reactivate(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['employees'] });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

/** Staff / HR admin: ask the company's HR admins to remove my account. */
export function useRequestAccountDeletion() {
  return useMutation({ mutationFn: (body: AccountDeletionRequestBody) => employeeApi.requestDeletion(body) });
}

// ---- Company (admin) ----
export const useCompany = () => useQuery({ queryKey: qk.company, queryFn: companyApi.me });

/**
 * OWNER: permanently delete the company and all of its data. No cache work on
 * success — the caller signs out, which clears every cached query.
 */
export function useDeleteCompany() {
  return useMutation({ mutationFn: (body: DeleteCompanyBody) => companyApi.remove(body) });
}

/** OWNER: the guided setup is done — hides the dashboard's "Finish setting up" card. */
export function useCompleteCompanySetup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => companyApi.completeSetup(),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['company'] }),
  });
}

export function useUpdateCompany() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateCompanyBody) => companyApi.update(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['company'] }),
  });
}

// ---- Company settings / payroll defaults (admin) ----
export const useCompanySettings = () =>
  useQuery({ queryKey: qk.companySettings, queryFn: companySettingsApi.get });

export function useUpdateCompanySettings() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateCompanySettingsBody) => companySettingsApi.update(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['companySettings'] }),
  });
}
