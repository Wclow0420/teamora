import { api } from './client';
import {
  ApplyLeaveBody,
  AttendanceSummary,
  AuthResponse,
  ChangeRoleBody,
  Claim,
  ClaimSummary,
  CompanyResponse,
  CreateEmployeeBody,
  DashboardSummary,
  EmployeeResponse,
  ManagerOption,
  LeaveBalance,
  LeaveRequest,
  LiveAttendance,
  MonthCalendar,
  NotificationList,
  WeekSchedule,
  AssignShiftBody,
  ShiftRow,
  Overtime,
  PendingOvertime,
  SubmitOvertimeBody,
  Payslip,
  PayrollRun,
  PayrollSummary,
  PendingClaim,
  PendingLeave,
  RegisterBody,
  SubmitClaimBody,
  TodayStatus,
  UpdateCompanyBody,
  UpdateEmployeeBody,
} from './types';

export const authApi = {
  login: (email: string, password: string) => api.post<AuthResponse>('/api/auth/login', { email, password }, false),
  register: (body: RegisterBody) => api.post<AuthResponse>('/api/auth/register', body, false),
  refresh: (refreshToken: string) => api.post<AuthResponse>('/api/auth/refresh', { refreshToken }, false),
  logout: (refreshToken: string) => api.post<void>('/api/auth/logout', { refreshToken }),
  me: () => api.get<EmployeeResponse>('/api/auth/me'),
};

export const employeeApi = {
  me: () => api.get<EmployeeResponse>('/api/employees/me'),
  list: (dept?: string, q?: string) => {
    const params = new URLSearchParams();
    if (dept) params.set('dept', dept);
    if (q) params.set('q', q);
    const qs = params.toString();
    return api.get<EmployeeResponse[]>(`/api/employees${qs ? `?${qs}` : ''}`);
  },
  create: (body: CreateEmployeeBody) => api.post<EmployeeResponse>('/api/employees', body),
  changeRole: (id: string, body: ChangeRoleBody) => api.patch<EmployeeResponse>(`/api/employees/${id}/role`, body),
  update: (id: string, body: UpdateEmployeeBody) => api.patch<EmployeeResponse>(`/api/employees/${id}`, body),
  managers: () => api.get<ManagerOption[]>('/api/employees/managers'),
  transferOwnership: (id: string) => api.post<EmployeeResponse>(`/api/employees/${id}/transfer-ownership`),
};

export const companyApi = {
  me: () => api.get<CompanyResponse>('/api/companies/me'),
  update: (body: UpdateCompanyBody) => api.patch<CompanyResponse>('/api/companies/me', body),
};

export const attendanceApi = {
  today: () => api.get<TodayStatus>('/api/attendance/today'),
  history: (month?: string) => api.get<AttendanceSummary>(`/api/attendance/me${month ? `?month=${month}` : ''}`),
  clockIn: () => api.post<TodayStatus>('/api/attendance/clock-in'),
  clockOut: () => api.post<TodayStatus>('/api/attendance/clock-out'),
  live: () => api.get<LiveAttendance>('/api/admin/attendance/live'),
};

export const leaveApi = {
  balances: () => api.get<LeaveBalance[]>('/api/leave/balances'),
  requests: () => api.get<LeaveRequest[]>('/api/leave/requests'),
  apply: (body: ApplyLeaveBody) => api.post<LeaveRequest>('/api/leave/requests', body),
  pending: () => api.get<PendingLeave[]>('/api/admin/leave/requests?status=PENDING'),
  approve: (id: string) => api.post<void>(`/api/admin/leave/requests/${id}/approve`),
  reject: (id: string) => api.post<void>(`/api/admin/leave/requests/${id}/reject`),
};

export const claimApi = {
  summary: () => api.get<ClaimSummary>('/api/claims'),
  submit: (body: SubmitClaimBody) => api.post<Claim>('/api/claims', body),
  pending: () => api.get<PendingClaim[]>('/api/admin/claims?status=PENDING'),
  approve: (id: string) => api.post<void>(`/api/admin/claims/${id}/approve`),
  reject: (id: string) => api.post<void>(`/api/admin/claims/${id}/reject`),
};

export const overtimeApi = {
  mine: () => api.get<Overtime[]>('/api/overtime'),
  submit: (body: SubmitOvertimeBody) => api.post<Overtime>('/api/overtime', body),
  pending: () => api.get<PendingOvertime[]>('/api/admin/overtime'),
  approve: (id: string) => api.post<void>(`/api/admin/overtime/${id}/approve`),
  reject: (id: string) => api.post<void>(`/api/admin/overtime/${id}/reject`),
};

export const calendarApi = {
  month: (month?: string) => api.get<MonthCalendar>(`/api/calendar${month ? `?month=${month}` : ''}`),
};

export const notificationApi = {
  list: () => api.get<NotificationList>('/api/notifications'),
  readAll: () => api.post<void>('/api/notifications/read-all'),
  registerPushToken: (token: string, platform: string) =>
    api.post<void>('/api/notifications/push-token', { token, platform }),
  removePushToken: (token: string) => api.del<void>(`/api/notifications/push-token?token=${encodeURIComponent(token)}`),
};

export const scheduleApi = {
  week: (weekStart?: string) => api.get<WeekSchedule>(`/api/admin/schedule${weekStart ? `?weekStart=${weekStart}` : ''}`),
  assign: (body: AssignShiftBody) => api.post<ShiftRow>('/api/admin/schedule', body),
};

export const dashboardApi = {
  summary: () => api.get<DashboardSummary>('/api/admin/dashboard'),
};

export const payrollApi = {
  payslips: () => api.get<Payslip[]>('/api/payroll/payslips'),
  payslip: (period: string) => api.get<Payslip>(`/api/payroll/payslips/${period}`),
  summary: (period?: string) => api.get<PayrollSummary>(`/api/admin/payroll/summary${period ? `?period=${period}` : ''}`),
  runView: (period: string) => api.get<PayrollRun>(`/api/admin/payroll/run?period=${period}`),
  run: (period: string) => api.post<PayrollRun>('/api/admin/payroll/run', { period }),
  approveRun: (period: string) => api.post<PayrollRun>(`/api/admin/payroll/run/${period}/approve`),
  markPaid: (period: string) => api.post<PayrollRun>(`/api/admin/payroll/run/${period}/mark-paid`),
};
