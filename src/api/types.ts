/** TypeScript mirrors of the backend JSON DTOs (see backend/.../dto). */

export type Role = 'OWNER' | 'HR_ADMIN' | 'MANAGER' | 'EMPLOYEE';

/** Marital status — drives the PCB (income tax) estimate. */
export type MaritalStatus = 'SINGLE' | 'MARRIED';

/** Not an EMPLOYEE — can be a reporting manager / approver. */
export function isManagementRole(role: Role | null | undefined): boolean {
  return role != null && role !== 'EMPLOYEE';
}

/**
 * Who gets the HR admin app. OWNER + HR_ADMIN only. MANAGERs are approvers who
 * live in the staff app (with an Approvals inbox), EMPLOYEEs in the staff app.
 */
export function isAdminRole(role: Role | null | undefined): boolean {
  return role === 'OWNER' || role === 'HR_ADMIN';
}

export type EmployeeResponse = {
  id: string;
  email: string;
  fullName: string;
  initial: string;
  role: Role;
  jobTitle: string | null;
  department: string | null;
  location: string | null;
  staffId: string | null;
  phone: string | null;
  joinDate: string | null;
  active: boolean;
  companyId: string | null;
  companyName: string | null;
  reportingManagerId: string | null;
  reportingManagerName: string | null;
  monthlySalary: number | null;
  maritalStatus: MaritalStatus | null;
  spouseWorking: boolean | null;
  numChildren: number;
};

export type ManagerOption = { id: string; fullName: string; role: Role; jobTitle: string | null };

export type AuthResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  employee: EmployeeResponse;
};

// ---- Attendance ----
export type AttendanceDay = {
  date: string;
  weekday: string;
  clockIn: string | null;
  clockOut: string | null;
  status: string;
  statusLabel: string;
  workedLabel: string;
  live: boolean;
};
export type AttendanceSummary = {
  month: string;
  present: number;
  late: number;
  leave: number;
  otHoursLabel: string;
  weeklyHours: number[];
  weeklyTotalLabel: string;
  days: AttendanceDay[];
};
export type TodayStatus = {
  status: string;
  clockInAt: string | null;
  workedMinutes: number | null;
  shift: string;
  location: string;
};
export type LiveStaffRow = {
  employeeId: string;
  name: string;
  initial: string;
  department: string;
  status: string;
  time: string;
  accentColorKey: string;
};
export type LiveAttendance = {
  counts: { inOffice: number; remote: number; late: number; out: number };
  staff: LiveStaffRow[];
};

// ---- Leave ----
export type LeaveBalance = { type: string; label: string; used: number; entitled: number; remaining: number };
export type LeaveRequest = {
  id: string;
  type: string;
  typeLabel: string;
  dateRangeLabel: string;
  durationLabel: string;
  reason: string | null;
  status: string;
  statusLabel: string;
};
export type PendingLeave = {
  id: string;
  employeeName: string;
  initial: string;
  type: string;
  typeLabel: string;
  dateRangeLabel: string;
  durationLabel: string;
  reason: string | null;
  balanceLabel: string;
  accentColorKey: string;
};
export type ApplyLeaveBody = { leaveType: string; startDate: string; endDate: string; reason?: string };

// ---- Claims ----
export type Claim = {
  id: string;
  category: string;
  title: string;
  amountLabel: string;
  amount: number;
  claimDateLabel: string;
  status: string;
  statusLabel: string;
};
export type ClaimSummary = { pendingTotalLabel: string; reimbursedThisMonthLabel: string; claims: Claim[] };
export type PendingClaim = {
  id: string;
  employeeName: string;
  initial: string;
  title: string;
  category: string;
  amountLabel: string;
  claimDateLabel: string;
  accentColorKey: string;
};
export type SubmitClaimBody = { category: string; title: string; amount: number; claimDate: string; receiptUrl?: string };

// ---- Overtime ----
export type Overtime = {
  id: string;
  workDateLabel: string;
  hoursLabel: string;
  reason: string | null;
  status: string;
  statusLabel: string;
};
export type PendingOvertime = {
  id: string;
  employeeName: string;
  initial: string;
  workDateLabel: string;
  hoursLabel: string;
  reason: string | null;
  accentColorKey: string;
};
export type SubmitOvertimeBody = { workDate: string; hours: number; reason?: string };

// ---- Payroll ----
export type Payslip = {
  id: string;
  period: string;
  periodLabel: string;
  netLabel: string;
  net: number;
  basicLabel: string;
  overtimeLabel: string;
  claimsLabel: string;
  bonusLabel: string;
  deductionsLabel: string;
  epfLabel: string;
  socsoLabel: string;
  eisLabel: string;
  pcbLabel: string;
  payDateLabel: string;
  status: string;
  statusLabel: string;
  bankLabel: string;
};
export type PayrollSummary = {
  period: string;
  periodLabel: string;
  employeeCount: number;
  grossLabel: string;
  epfLabel: string;
  socsoEisLabel: string;
  netLabel: string;
  net: number;
  payDateLabel: string;
  status: string;
  statusLabel: string;
};

/** Payslip / payroll-run lifecycle status. */
export type PayslipStatus = 'DRAFT' | 'IN_REVIEW' | 'APPROVED' | 'PAID';

export type PayrollRunLine = {
  payslipId: string;
  employeeName: string;
  initial: string;
  department: string | null;
  basicLabel: string;
  overtimeLabel: string;
  claimsLabel: string;
  grossLabel: string;
  epfLabel: string;
  socsoLabel: string;
  eisLabel: string;
  pcbLabel: string;
  deductionsLabel: string;
  netLabel: string;
  status: PayslipStatus;
};

export type PayrollRun = {
  period: string;
  periodLabel: string;
  generated: boolean;
  employeeCount: number;
  skippedCount: number;
  grossLabel: string;
  statutoryLabel: string;
  pcbLabel: string;
  netLabel: string;
  net: number;
  payDateLabel: string;
  status: PayslipStatus;
  statusLabel: string;
  lines: PayrollRunLine[];
};

// ---- Company / employee management ----
export type RegisterBody = { companyName: string; fullName: string; email: string; password: string };

export type CompanyResponse = {
  id: string;
  name: string;
  slug: string;
  registrationNo: string | null;
  epfNo: string | null;
  socsoNo: string | null;
  email: string | null;
  phone: string | null;
  address: string | null;
  timezone: string;
  currency: string;
  active: boolean;
};
export type UpdateCompanyBody = {
  name?: string;
  registrationNo?: string;
  epfNo?: string;
  socsoNo?: string;
  email?: string;
  phone?: string;
  address?: string;
};

export type CreateEmployeeBody = {
  fullName: string;
  email: string;
  password: string;
  role: Role;
  jobTitle?: string;
  department?: string;
  staffId?: string;
  phone?: string;
  reportingManagerId?: string;
  monthlySalary?: number;
  maritalStatus?: MaritalStatus;
  spouseWorking?: boolean;
  numChildren?: number;
};
export type ChangeRoleBody = { role: Role };
export type UpdateEmployeeBody = {
  fullName?: string;
  jobTitle?: string;
  department?: string;
  phone?: string;
  staffId?: string;
  role?: Role;
  reportingManagerId?: string;
  monthlySalary?: number;
  maritalStatus?: MaritalStatus;
  spouseWorking?: boolean;
  numChildren?: number;
};

// ---- Calendar ----
export type UpcomingEvent = { iconName: string; title: string; dateLabel: string; accentColorKey: string };
export type MonthCalendar = { monthLabel: string; events: Record<string, string[]>; upcoming: UpcomingEvent[] };

// ---- Notifications ----
export type NotificationItem = {
  id: string;
  iconName: string;
  title: string;
  body: string | null;
  timeLabel: string;
  accentColorKey: string;
  unread: boolean;
};
export type NotificationList = { today: NotificationItem[]; earlier: NotificationItem[]; unreadCount: number };

// ---- Scheduling ----
export type ShiftRow = {
  employeeId: string;
  employeeName: string;
  initial: string;
  department: string | null;
  shiftType: string;
  shiftLabel: string;
  timeLabel: string;
  shiftColorKey: string;
};
export type DayShifts = { date: string; weekdayLabel: string; dayLabel: string; onShift: number; shifts: ShiftRow[] };
export type WeekSchedule = { weekLabel: string; days: DayShifts[] };
export type AssignShiftBody = { employeeId: string; date: string; shiftType: string };

// ---- Dashboard (admin) ----
export type DashboardActivity = {
  type: 'leave' | 'claim' | 'overtime';
  text: string;
  timeLabel: string;
  accent: string; // sage | amber | coral | violet
};
export type DashboardWeek = { labels: string[]; present: number[]; max: number; rateLabel: string };
export type DashboardSummary = {
  headcount: number;
  presentToday: number;
  onLeaveToday: number;
  pendingApprovals: number;
  payrollDueLabel: string;
  week: DashboardWeek;
  activity: DashboardActivity[];
};

/** Shape of the backend's ApiError body. */
export type ApiErrorBody = {
  timestamp?: string;
  status: number;
  error: string;
  message: string;
  path?: string;
  fieldErrors?: Record<string, string>;
};
