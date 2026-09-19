import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { attendanceApi, calendarApi, claimApi, companyApi, dashboardApi, employeeApi, leaveApi, notificationApi, overtimeApi, payrollApi, scheduleApi } from './endpoints';
import type { ApplyLeaveBody, AssignShiftBody, CreateEmployeeBody, Role, SubmitClaimBody, SubmitOvertimeBody, UpdateCompanyBody, UpdateEmployeeBody } from './types';

/** Centralised query keys. */
export const qk = {
  me: ['me'] as const,
  today: ['attendance', 'today'] as const,
  attendance: (month?: string) => ['attendance', 'history', month ?? 'current'] as const,
  liveAttendance: ['attendance', 'live'] as const,
  leaveBalances: ['leave', 'balances'] as const,
  leaveRequests: ['leave', 'requests'] as const,
  pendingLeave: ['leave', 'pending'] as const,
  claims: ['claims', 'me'] as const,
  pendingClaims: ['claims', 'pending'] as const,
  payslips: ['payroll', 'payslips'] as const,
  payslip: (period: string) => ['payroll', 'payslip', period] as const,
  payrollSummary: (period?: string) => ['payroll', 'summary', period ?? 'latest'] as const,
  payrollRun: (period: string) => ['payroll', 'run', period] as const,
  staff: (dept?: string, q?: string) => ['employees', dept ?? 'all', q ?? ''] as const,
  company: ['company', 'me'] as const,
};

// ---- Profile ----
export const useMe = () => useQuery({ queryKey: qk.me, queryFn: employeeApi.me });

// ---- Attendance ----
export const useTodayAttendance = () => useQuery({ queryKey: qk.today, queryFn: attendanceApi.today });
export const useAttendanceHistory = (month?: string) =>
  useQuery({ queryKey: qk.attendance(month), queryFn: () => attendanceApi.history(month) });
export const useLiveAttendance = () =>
  useQuery({ queryKey: qk.liveAttendance, queryFn: attendanceApi.live, refetchInterval: 30_000 });

export function useClockIn() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: attendanceApi.clockIn,
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
export const useLeaveBalances = () => useQuery({ queryKey: qk.leaveBalances, queryFn: leaveApi.balances });
export const useLeaveRequests = () => useQuery({ queryKey: qk.leaveRequests, queryFn: leaveApi.requests });
export const usePendingLeave = () => useQuery({ queryKey: qk.pendingLeave, queryFn: leaveApi.pending });

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
    mutationFn: ({ id, decision }: { id: string; decision: 'approve' | 'reject' }) =>
      decision === 'approve' ? leaveApi.approve(id) : leaveApi.reject(id),
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
    mutationFn: ({ id, decision }: { id: string; decision: 'approve' | 'reject' }) =>
      decision === 'approve' ? claimApi.approve(id) : claimApi.reject(id),
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
    mutationFn: ({ id, decision }: { id: string; decision: 'approve' | 'reject' }) =>
      decision === 'approve' ? overtimeApi.approve(id) : overtimeApi.reject(id),
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

// ---- Calendar ----
export const useCalendar = (month?: string) =>
  useQuery({ queryKey: ['calendar', month ?? 'current'], queryFn: () => calendarApi.month(month) });

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

// ---- Company (admin) ----
export const useCompany = () => useQuery({ queryKey: qk.company, queryFn: companyApi.me });

export function useUpdateCompany() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateCompanyBody) => companyApi.update(body),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['company'] }),
  });
}
