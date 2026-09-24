import { api } from './client';
import { API_BASE_URL } from './config';
import {
  ApplyLeaveBody,
  AttendanceSummary,
  AuthResponse,
  ChangeRoleBody,
  Claim,
  ClaimSummary,
  ClockInBody,
  CompanyEventItem,
  CompanyResponse,
  CompanySettings,
  CreateCompanyEventBody,
  CreateEmployeeBody,
  CreateLeaveTypeBody,
  CreateWorkLocationBody,
  DashboardSummary,
  EmployeeResponse,
  ExportFile,
  ExportType,
  ManagerOption,
  LeaveBalance,
  LeaveRequest,
  LeaveTypeDef,
  LiveAttendance,
  MonthCalendar,
  NotificationList,
  OverrideLeaveEntitlementBody,
  WeekSchedule,
  AssignShiftBody,
  ShiftRow,
  Overtime,
  PendingOvertime,
  SubmitOvertimeBody,
  Payslip,
  PayrollRun,
  PayrollSummary,
  StatutorySummary,
  PendingClaim,
  PendingLeave,
  RegisterBody,
  SubmitClaimBody,
  TodayStatus,
  UpdateCompanyBody,
  UpdateCompanyEventBody,
  UpdateCompanySettingsBody,
  UpdateEmployeeBody,
  UpdateLeaveTypeBody,
  UpdateWorkLocationBody,
  WorkLocation,
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
  get: (id: string) => api.get<EmployeeResponse>(`/api/employees/${id}`),
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

export const companySettingsApi = {
  get: () => api.get<CompanySettings>('/api/admin/company-settings'),
  update: (body: UpdateCompanySettingsBody) =>
    api.patch<CompanySettings>('/api/admin/company-settings', body),
};

export const leaveTypeApi = {
  /** Active types for staff pickers. */
  active: () => api.get<LeaveTypeDef[]>('/api/leave/types'),
  /** All types (incl. inactive) for admin management. */
  all: () => api.get<LeaveTypeDef[]>('/api/admin/leave/types'),
  create: (body: CreateLeaveTypeBody) => api.post<LeaveTypeDef>('/api/admin/leave/types', body),
  update: (id: string, body: UpdateLeaveTypeBody) =>
    api.patch<LeaveTypeDef>(`/api/admin/leave/types/${id}`, body),
};

export const attendanceApi = {
  today: () => api.get<TodayStatus>('/api/attendance/today'),
  history: (month?: string) => api.get<AttendanceSummary>(`/api/attendance/me${month ? `?month=${month}` : ''}`),
  clockIn: (body?: ClockInBody) => api.post<TodayStatus>('/api/attendance/clock-in', body),
  clockOut: () => api.post<TodayStatus>('/api/attendance/clock-out'),
  live: () => api.get<LiveAttendance>('/api/admin/attendance/live'),
  /**
   * Absolute URL for an attendance record's clock-in selfie. The endpoint is
   * auth-guarded, so callers must attach the Bearer token themselves (e.g. RN
   * `<Image>` source headers) — see the token store.
   */
  photoUrl: (recordId: string) => `${API_BASE_URL}/api/attendance/records/${recordId}/photo`,
};

export const workLocationApi = {
  /** Active sites for the caller's company (staff-facing). */
  list: () => api.get<WorkLocation[]>('/api/work-locations'),
  /** All sites incl. inactive (admin management). */
  adminList: () => api.get<WorkLocation[]>('/api/admin/work-locations'),
  create: (body: CreateWorkLocationBody) => api.post<WorkLocation>('/api/admin/work-locations', body),
  update: (id: string, body: UpdateWorkLocationBody) =>
    api.patch<WorkLocation>(`/api/admin/work-locations/${id}`, body),
};

export const leaveApi = {
  /** My balances. `year` names a leave year by its starting calendar year; omit for the current one. */
  balances: (year?: number) =>
    api.get<LeaveBalance[]>(`/api/leave/balances${year != null ? `?year=${year}` : ''}`),
  requests: () => api.get<LeaveRequest[]>('/api/leave/requests'),
  apply: (body: ApplyLeaveBody) => api.post<LeaveRequest>('/api/leave/requests', body),
  pending: () => api.get<PendingLeave[]>('/api/admin/leave/requests?status=PENDING'),
  approve: (id: string) => api.post<void>(`/api/admin/leave/requests/${id}/approve`),
  reject: (id: string) => api.post<void>(`/api/admin/leave/requests/${id}/reject`),
};

/** Admin view of a single employee's leave balances + entitlement overrides. */
export const leaveBalanceAdminApi = {
  list: (employeeId: string, year?: number) => {
    const params = new URLSearchParams({ employeeId });
    if (year != null) params.set('year', String(year));
    return api.get<LeaveBalance[]>(`/api/admin/leave/balances?${params.toString()}`);
  },
  override: (body: OverrideLeaveEntitlementBody) =>
    api.patch<LeaveBalance>('/api/admin/leave/balances', body),
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

/**
 * Admin CRUD over company calendar events. HOLIDAY rows feed payroll
 * (holiday pay), so edits here affect future payroll runs.
 */
export const calendarAdminApi = {
  list: (month?: string) =>
    api.get<CompanyEventItem[]>(`/api/admin/calendar/events${month ? `?month=${month}` : ''}`),
  create: (body: CreateCompanyEventBody) => api.post<CompanyEventItem>('/api/admin/calendar/events', body),
  update: (id: string, body: UpdateCompanyEventBody) =>
    api.patch<CompanyEventItem>(`/api/admin/calendar/events/${id}`, body),
  remove: (id: string) => api.del<void>(`/api/admin/calendar/events/${id}`),
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

export const payrollExportApi = {
  /** Statutory contribution summary (on-screen) for a period's persisted payslips. */
  summary: (period: string) =>
    api.get<StatutorySummary>(`/api/admin/payroll/export/summary?period=${encodeURIComponent(period)}`),
  /** A single export file (CSV/text) for a period — fetched on demand, not cached. */
  file: (period: string, type: ExportType) =>
    api.get<ExportFile>(
      `/api/admin/payroll/export/file?period=${encodeURIComponent(period)}&type=${type}`,
    ),
};
