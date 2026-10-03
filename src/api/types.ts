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
  /** Start of the work day, "HH:mm" (24h). Optional: an older backend doesn't send it. */
  workStartTime?: string | null;
  /** Minutes after `workStartTime` before a clock-in counts as late (0–120). */
  lateGraceMinutes?: number | null;
};
export type UpdateCompanySettingsBody = {
  workStartTime?: string;
  lateGraceMinutes?: number;
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
  /**
   * The employee's OWN stored pay settings. `null` = no personal value (the
   * effective field above is the company default). Absent on an older backend.
   */
  payBasisOverride?: PayBasis | null;
  workingDaysOverride?: number | null;
  hoursPerDayOverride?: number | null;
};

/** Self-service password change (`POST /api/auth/change-password` → 204). */
export type ChangePasswordBody = {
  currentPassword: string;
  newPassword: string;
  /** This device's refresh token — the server keeps that one session and revokes the rest. */
  refreshToken?: string;
};

/** Ask for a one-time reset code (`POST /api/auth/forgot-password`, public → always 200). */
export type ForgotPasswordBody = { email: string };
export type ForgotPasswordResponse = {
  /**
   * The code itself — only ever sent by a local/dev backend that has
   * `PASSWORD_RESET_EXPOSE_CODE` on. Null/absent everywhere else.
   */
  devCode?: string | null;
};
/** Redeem a reset code (`POST /api/auth/reset-password`, public → 204). */
export type ResetPasswordWithCodeBody = { email: string; code: string; newPassword: string };

/** Admin sets a temporary password (`POST /api/employees/{id}/reset-password` → 204). */
export type ResetPasswordBody = { newPassword: string };

/** The only self-editable field (`PATCH /api/employees/me`). A blank phone clears it. */
export type UpdateMyDetailsBody = { phone?: string };

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
  /**
   * When they last clocked out (ISO instant); null while working — including
   * after clocking in again the same day. Optional: an older backend doesn't send it.
   */
  clockOutAt?: string | null;
  /** Minutes spent clocked out between sessions today (0 = none). Optional as above. */
  breakMinutes?: number;
  shift: string;
  /** The assigned work site's name; null when the employee has no site. */
  location: string | null;
};
export type LiveStaffRow = {
  employeeId: string;
  name: string;
  initial: string;
  department: string | null;
  /** AttendanceStatus: WORKING | PRESENT | LATE | REMOTE | ON_LEAVE | ABSENT. */
  status: string;
  time: string;
  accentColorKey: string;
  /** Real work-site name for today's clock-in; null/absent when there is none. */
  location?: string | null;
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
  /**
   * How the type accrues. `NONE` = untracked (e.g. unpaid leave): there is no
   * balance to show, so UIs must skip the balance tile and leave it out of totals.
   */
  accrual: LeaveAccrual;
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
  /** The approver's reason when the request was declined (optional; older servers omit it). */
  decisionNote?: string | null;
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
  /** True when a receipt photo is stored — fetch it via `claimApi.receiptUrl(id)`. */
  hasReceipt: boolean;
  /** The approver's reason when the claim was declined (optional; older servers omit it). */
  decisionNote?: string | null;
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
  /** True when the claimant attached a receipt photo. */
  hasReceipt: boolean;
};
/**
 * `receiptBase64` is an optional back-camera JPEG (bare base64, no `data:`
 * prefix; server caps it at 2 MB). A claim is valid without one.
 */
export type SubmitClaimBody = {
  category: string;
  title: string;
  amount: number;
  claimDate: string;
  receiptUrl?: string;
  receiptBase64?: string;
};

// ---- Overtime ----
export type Overtime = {
  id: string;
  workDateLabel: string;
  hoursLabel: string;
  reason: string | null;
  status: string;
  statusLabel: string;
  /** The approver's reason when the request was declined (optional; older servers omit it). */
  decisionNote?: string | null;
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
  /** "Maybank ••1234", bank name only, or null when HR has no bank details on file. */
  bankLabel: string | null;
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
  /**
   * Unpaid-leave transparency. `unpaidDeductionLabel` is null when nothing was
   * deducted; all three are optional so an older backend still parses.
   */
  unpaidDays?: number;
  unpaidDaysLabel?: string | null;
  unpaidDeductionLabel?: string | null;
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
  /** ISO yyyy-MM-dd. Drives first-year leave proration. */
  joinDate?: string;
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
  /** ISO yyyy-MM-dd. Drives first-year leave proration. */
  joinDate?: string;
  jobTitle?: string;
  department?: string;
  phone?: string;
  staffId?: string;
  role?: Role;
  /**
   * Assign a reporting manager. The update is *partial*: send the id to set it,
   * send `clearReportingManager: true` to clear it, and send NEITHER to leave
   * the current manager untouched (what every screen that doesn't own this
   * field must do).
   */
  reportingManagerId?: string;
  /** Clear the reporting manager (approvals fall back to the company owner). */
  clearReportingManager?: boolean;
  /** Assign a work site. Same partial semantics as `reportingManagerId`. */
  workLocationId?: string | null;
  /** Clear the assigned work site (no geofence). */
  clearWorkLocation?: boolean;
  monthlySalary?: number;
  /** Clear the salary (the employee is then not on payroll). Never send with `monthlySalary`. */
  clearMonthlySalary?: boolean;
  maritalStatus?: MaritalStatus;
  spouseWorking?: boolean;
  numChildren?: number;
  payBasis?: PayBasis;
  workingDays?: number;
  hoursPerDay?: number;
  /**
   * Drop the employee's own override so they follow the company default again.
   * Same partial semantics as `clearReportingManager`: send the value OR its
   * clear flag, never both (the server rejects that with a 400).
   */
  clearPayBasis?: boolean;
  clearWorkingDays?: boolean;
  clearHoursPerDay?: boolean;
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
  /** e.g. "September 2026". */
  periodLabel?: string;
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

/** One suggested Malaysian public holiday from the server's catalogue. */
export type HolidaySuggestion = {
  /** ISO date (YYYY-MM-DD). */
  date: string;
  name: string;
  /** Short regional caveat, e.g. "Not observed in Sarawak"; null when it applies nationwide. */
  note: string | null;
  /** The company already has a HOLIDAY event on this date. */
  alreadyAdded: boolean;
};

/** `GET /api/admin/calendar/holiday-suggestions?year=` — a suggested list the admin reviews. */
export type HolidaySuggestions = {
  year: number;
  /** Plain-language provenance line shown under the list. */
  source: string;
  /** Years available in the catalogue. */
  years: number[];
  items: HolidaySuggestion[];
};

export type ImportHolidayItem = { date: string; name: string };
export type ImportHolidaysResult = { created: number; skipped: number };

/** Optional decline reason sent to the reject endpoints (only when non-empty). */
export type RejectBody = { reason: string };

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
