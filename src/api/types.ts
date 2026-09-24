/** TypeScript mirrors of the backend JSON DTOs (see backend/.../dto). */

export type Role = 'OWNER' | 'HR_ADMIN' | 'MANAGER' | 'EMPLOYEE';

/** Marital status — drives the PCB (income tax) estimate. */
export type MaritalStatus = 'SINGLE' | 'MARRIED';

/**
 * How a staff member is paid. Monthly salary is always the stored input; the
 * daily/hourly rates derive from it (see the compensation engine). Company sets
 * a default; each employee may override.
 */
export type PayBasis = 'MONTHLY' | 'DAILY' | 'HOURLY';

/** How a leave type's entitlement accrues. */
export type LeaveAccrual = 'FIXED_ANNUAL' | 'MONTHLY_ACCRUAL' | 'NONE';

/**
 * A company-configurable leave type (replaces the old hardcoded enum). `paid`
 * decides whether the leave deducts salary; `colorKey` maps to a theme accent.
 */
export type LeaveTypeDef = {
  id: string;
  name: string;
  code: string;
  paid: boolean;
  defaultEntitlementDays: number;
  accrual: LeaveAccrual;
  /** Max unused days carried into the next leave year. 0 = forfeited at year end. */
  carryForwardMaxDays: number;
  colorKey: string;
  active: boolean;
  sortOrder: number;
};

/** Company-wide compensation/schedule defaults applied to new staff. */
export type CompanySettings = {
  defaultPayBasis: PayBasis;
  /** Working-weekday bitmask: bit0=Mon … bit6=Sun. Mon–Fri = 31, full week = 127. */
  defaultWorkingDays: number;
  defaultHoursPerDay: number;
  /** Month (1–12) the company's leave year begins. 1 = calendar year. */
  leaveYearStartMonth: number;
};
export type UpdateCompanySettingsBody = {
  defaultPayBasis?: PayBasis;
  defaultWorkingDays?: number;
  defaultHoursPerDay?: number;
  leaveYearStartMonth?: number;
};

export type CreateLeaveTypeBody = {
  name: string;
  code: string;
  paid: boolean;
  defaultEntitlementDays: number;
  accrual?: LeaveAccrual;
  carryForwardMaxDays?: number;
  colorKey: string;
  sortOrder?: number;
};
export type UpdateLeaveTypeBody = {
  name?: string;
  paid?: boolean;
  defaultEntitlementDays?: number;
  accrual?: LeaveAccrual;
  carryForwardMaxDays?: number;
  colorKey?: string;
  active?: boolean;
  sortOrder?: number;
};

// ---- Work locations (geofencing) ----
/** A company work site: a GPS pin + a radius staff must be within to clock in. */
export type WorkLocation = {
  id: string;
  name: string;
  latitude: number;
  longitude: number;
  radiusM: number;
  active: boolean;
};
export type CreateWorkLocationBody = {
  name: string;
  latitude: number;
  longitude: number;
  radiusM?: number;
  active?: boolean;
};
export type UpdateWorkLocationBody = {
  name?: string;
  latitude?: number;
  longitude?: number;
  radiusM?: number;
  active?: boolean;
};

/**
 * Sent with a clock-in: coordinates for the server-side geofence check, plus an
 * optional front-camera selfie (bare base64 JPEG) as attendance proof. The photo
 * is optional/non-blocking — clock-in still succeeds without it.
 */
export type ClockInBody = { latitude?: number; longitude?: number; photoBase64?: string };

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
  /** Assigned work site for geofenced clock-in (null → no geofence). */
  workLocationId: string | null;
  workLocationName: string | null;
  monthlySalary: number | null;
  maritalStatus: MaritalStatus | null;
  spouseWorking: boolean | null;
  numChildren: number;
  /** Statutory & bank identity — needed for payroll/statutory exports. All nullable. */
  nric: string | null;
  epfNo: string | null;
  socsoNo: string | null;
  taxNo: string | null;
  bankName: string | null;
  bankAccountNo: string | null;
  /** Effective pay basis (own override else company default). */
  payBasis: PayBasis;
  /** Effective working-weekday bitmask (own override else company default). */
  workingDays: number;
  /** Effective hours worked per scheduled day. */
  hoursPerDay: number;
  /** Indicative derived rates for the CURRENT month (null when no salary set). */
  derivedDailyRate: number | null;
  derivedHourlyRate: number | null;
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
  /** Today's attendance record id — used to build the clock-in photo URL. */
  attendanceRecordId?: string | null;
  /** Whether a clock-in selfie is stored for today's record. */
  hasPhoto?: boolean;
};
export type LiveAttendance = {
  counts: { inOffice: number; remote: number; late: number; out: number };
  staff: LiveStaffRow[];
};

// ---- Leave ----
/** How much of a working day a leave request consumes. */
export type LeaveDurationUnit = 'FULL_DAY' | 'HALF_DAY' | 'HOURS';
/** Which half of the day a HALF_DAY request covers. */
export type HalfDayPeriod = 'AM' | 'PM';

export type LeaveBalance = {
  /** FK to the configurable leave type + its display metadata. */
  leaveTypeId: string;
  code: string;
  name: string;
  colorKey: string;
  paid: boolean;
  /** The leave year this row belongs to, named by its starting calendar year. */
  leaveYear: number;
  /** Fractional since partial-day leave — e.g. 12.5. Render via `formatDecimal`. */
  used: number;
  /** Full-year entitlement (already prorated for a first-year joiner). */
  entitled: number;
  /**
   * How much of `entitled` has accrued so far. Equals `entitled` for
   * FIXED_ANNUAL types; grows month by month for MONTHLY_ACCRUAL ones.
   */
  accruedToDate: number;
  /** Unused days brought in from the previous leave year (capped per type). */
  carriedForward: number;
  /** Available to take now = accruedToDate + carriedForward − used. */
  remaining: number;
};

/**
 * Admin entitlement override. `leaveYear` defaults to the current leave year
 * server-side when omitted.
 */
export type OverrideLeaveEntitlementBody = {
  employeeId: string;
  leaveTypeId: string;
  leaveYear?: number;
  entitled: number;
};
export type LeaveRequest = {
  id: string;
  leaveTypeId: string;
  typeCode: string;
  typeLabel: string;
  colorKey: string;
  paid: boolean;
  dateRangeLabel: string;
  /** Human duration, e.g. "3 days", "Half day (AM)", "2 hours". */
  durationLabel: string;
  durationUnit: LeaveDurationUnit;
  halfDayPeriod: HalfDayPeriod | null;
  /** Set only for HOURS requests, e.g. "2 hours". */
  hoursLabel: string | null;
  reason: string | null;
  status: string;
  statusLabel: string;
};
export type PendingLeave = {
  id: string;
  employeeName: string;
  initial: string;
  leaveTypeId: string;
  typeCode: string;
  typeLabel: string;
  paid: boolean;
  dateRangeLabel: string;
  durationLabel: string;
  durationUnit: LeaveDurationUnit;
  halfDayPeriod: HalfDayPeriod | null;
  hoursLabel: string | null;
  reason: string | null;
  balanceLabel: string;
  accentColorKey: string;
};
/**
 * HALF_DAY and HOURS apply to a single date — send `startDate === endDate`.
 * `durationUnit` defaults to FULL_DAY server-side when omitted.
 */
export type ApplyLeaveBody = {
  leaveTypeId: string;
  startDate: string;
  endDate: string;
  durationUnit?: LeaveDurationUnit;
  /** Required when `durationUnit` is HALF_DAY. */
  halfDayPeriod?: HalfDayPeriod;
  /** Required when `durationUnit` is HOURS — must be > 0 and <= the staff's hours/day. */
  hours?: number;
  /** Optional "HH:mm" start time, HOURS only. */
  startTime?: string;
  reason?: string;
};

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
  /**
   * Unpaid-leave transparency (present once the comp/leave engine ships). `basic`
   * is the paid basic (already net of any unpaid deduction); these fields explain
   * the reduction. Optional so pre-engine payslips still parse.
   */
  unpaidDays?: number;
  unpaidDeduction?: number;
  unpaidDaysLabel?: string;
  unpaidDeductionLabel?: string;
  dailyRateLabel?: string;
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
  /** Unpaid-leave transparency (present once the comp/leave engine ships). */
  unpaidDaysLabel?: string;
  unpaidDeductionLabel?: string;
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
  /** Assign a work site, or `null` to clear (no geofence). */
  workLocationId?: string | null;
  monthlySalary?: number;
  maritalStatus?: MaritalStatus;
  spouseWorking?: boolean;
  numChildren?: number;
  payBasis?: PayBasis;
  workingDays?: number;
  hoursPerDay?: number;
  /** Statutory & bank identity (all optional). */
  nric?: string;
  epfNo?: string;
  socsoNo?: string;
  taxNo?: string;
  bankName?: string;
  bankAccountNo?: string;
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
  /** Assign a work site, or `null` to clear (no geofence). */
  workLocationId?: string | null;
  monthlySalary?: number;
  maritalStatus?: MaritalStatus;
  spouseWorking?: boolean;
  numChildren?: number;
  payBasis?: PayBasis;
  workingDays?: number;
  hoursPerDay?: number;
  /** Statutory & bank identity (all optional). */
  nric?: string;
  epfNo?: string;
  socsoNo?: string;
  taxNo?: string;
  bankName?: string;
  bankAccountNo?: string;
};

// ---- Payroll statutory export ----
/**
 * One employee's statutory contribution line for a payroll period. Every money
 * value is a pre-formatted label from the backend (matching the app's label
 * convention); render with tabular-nums.
 */
export type StatutorySummaryRow = {
  employeeName: string;
  staffId: string | null;
  nric: string | null;
  epfNo: string | null;
  epfEmployeeLabel: string;
  epfEmployerLabel: string;
  socsoNo: string | null;
  socsoEmployeeLabel: string;
  socsoEmployerLabel: string;
  eisEmployeeLabel: string;
  eisEmployerLabel: string;
  taxNo: string | null;
  pcbLabel: string;
  netLabel: string;
};

/** Company totals row — same money label fields as a row, summed. */
export type StatutorySummaryTotals = {
  epfEmployeeLabel: string;
  epfEmployerLabel: string;
  socsoEmployeeLabel: string;
  socsoEmployerLabel: string;
  eisEmployeeLabel: string;
  eisEmployerLabel: string;
  pcbLabel: string;
  netLabel: string;
};

/** On-screen statutory contribution summary for a period (persisted payslips). */
export type StatutorySummary = {
  period: string;
  generatedAtLabel: string;
  rows: StatutorySummaryRow[];
  totals: StatutorySummaryTotals;
};

/** The export file types the backend can generate for a period. */
export type ExportType = 'contributions' | 'bank' | 'payroll' | 'cp39';

/** A downloadable export payload (CSV / plain text) returned by the backend. */
export type ExportFile = { filename: string; mimeType: string; content: string };

// ---- Calendar ----
export type UpcomingEvent = { iconName: string; title: string; dateLabel: string; accentColorKey: string };
export type MonthCalendar = { monthLabel: string; events: Record<string, string[]>; upcoming: UpcomingEvent[] };

/**
 * Company event types an admin can author. `BIRTHDAY` exists on the backend but
 * is derived from employee records, so it is never hand-created here.
 */
export type EventTypeValue = 'HOLIDAY' | 'EVENT' | 'TOWNHALL';

/** A single company calendar entry (admin CRUD view). */
export type CompanyEventItem = {
  id: string;
  title: string;
  /** ISO date (YYYY-MM-DD). */
  eventDate: string;
  eventType: EventTypeValue;
  accentColorKey: string;
  iconName: string;
  timeLabel: string | null;
};

export type CreateCompanyEventBody = {
  title: string;
  eventDate: string;
  eventType: EventTypeValue;
  timeLabel?: string | null;
};

export type UpdateCompanyEventBody = {
  title?: string;
  eventDate?: string;
  eventType?: EventTypeValue;
  timeLabel?: string | null;
};

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
