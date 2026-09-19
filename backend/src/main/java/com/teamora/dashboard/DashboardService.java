package com.teamora.dashboard;

import com.teamora.attendance.AttendanceRecord;
import com.teamora.attendance.AttendanceRepository;
import com.teamora.attendance.AttendanceStatus;
import com.teamora.claim.Claim;
import com.teamora.claim.ClaimRepository;
import com.teamora.claim.ClaimService;
import com.teamora.common.exception.ResourceNotFoundException;
import com.teamora.dashboard.dto.DashboardResponse;
import com.teamora.employee.Employee;
import com.teamora.employee.EmployeeRepository;
import com.teamora.leave.LeaveRequest;
import com.teamora.leave.LeaveRequestRepository;
import com.teamora.leave.LeaveService;
import com.teamora.overtime.OvertimeRepository;
import com.teamora.overtime.OvertimeRequest;
import com.teamora.overtime.OvertimeService;
import com.teamora.payroll.PayrollService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Aggregates the admin Dashboard summary from existing per-feature services/repos. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private static final ZoneId KL = ZoneId.of("Asia/Kuala_Lumpur");
    private static final List<String> WEEK_LABELS = List.of("M", "T", "W", "T", "F", "S", "S");

    private final EmployeeRepository employees;
    private final AttendanceRepository attendance;
    private final LeaveService leaveService;
    private final ClaimService claimService;
    private final OvertimeService overtimeService;
    private final LeaveRequestRepository leaveRequests;
    private final ClaimRepository claims;
    private final OvertimeRepository overtime;
    private final PayrollService payrollService;

    /** Is this status a "present at work" status (used for present counts)? */
    private static boolean isPresent(AttendanceStatus status) {
        return status == AttendanceStatus.PRESENT
                || status == AttendanceStatus.WORKING
                || status == AttendanceStatus.REMOTE
                || status == AttendanceStatus.LATE;
    }

    public DashboardResponse summary(Employee caller) {
        UUID companyId = caller.getCompany().getId();
        LocalDate today = LocalDate.now(KL);

        long headcount = employees.countByCompanyIdAndActiveTrue(companyId);

        // ---- Today's present / on-leave ----
        List<AttendanceRecord> todayRecords = attendance.findAllByWorkDateWithEmployee(companyId, today);
        long presentToday = todayRecords.stream().filter(r -> isPresent(r.getStatus())).count();
        long onLeaveToday = todayRecords.stream()
                .filter(r -> r.getStatus() == AttendanceStatus.ON_LEAVE).count();

        // ---- Pending approvals (role-routed, reusing the existing service methods) ----
        long pendingApprovals = (long) leaveService.pending(caller).size()
                + claimService.pending(caller).size()
                + overtimeService.pending(caller).size();

        // ---- Payroll due (current month's net total label, no "RM " prefix) ----
        String payrollDueLabel = currentPayrollNetLabel(companyId);

        // ---- Week chart (current ISO week, Mon..Sun) ----
        DashboardResponse.Week week = buildWeek(companyId, today, headcount);

        // ---- Activity feed (merge recent leave/claim/overtime, newest first, take 6) ----
        List<DashboardResponse.Activity> activity = buildActivity(companyId);

        return new DashboardResponse(
                headcount, presentToday, onLeaveToday, pendingApprovals, payrollDueLabel, week, activity);
    }

    private String currentPayrollNetLabel(UUID companyId) {
        String period = YearMonth.now(KL).toString();
        try {
            return payrollService.summary(period, companyId).netLabel();
        } catch (ResourceNotFoundException e) {
            // No payslips for the current month yet — fall back to the company's latest run.
            try {
                return payrollService.summary(null, companyId).netLabel();
            } catch (ResourceNotFoundException e2) {
                return "0.00";
            }
        }
    }

    private DashboardResponse.Week buildWeek(UUID companyId, LocalDate today, long headcount) {
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);

        List<AttendanceRecord> weekRecords = attendance.findAllByWorkDateRange(companyId, monday, sunday);

        // Distinct present employees per weekday (Mon=0 .. Sun=6).
        List<Set<UUID>> presentByDay = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            presentByDay.add(new HashSet<>());
        }
        for (AttendanceRecord r : weekRecords) {
            if (isPresent(r.getStatus())) {
                int idx = r.getWorkDate().getDayOfWeek().getValue() - 1;
                presentByDay.get(idx).add(r.getEmployee().getId());
            }
        }

        List<Integer> present = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            present.add(presentByDay.get(i).size());
        }

        // Rate so far this week: sum(present Mon..today) / (headcount * elapsed weekdays with data).
        int elapsedDays = today.getDayOfWeek().getValue(); // Mon=1 .. Sun=7
        long presentSum = 0;
        int daysWithData = 0;
        for (int i = 0; i < elapsedDays && i < 7; i++) {
            int count = present.get(i);
            presentSum += count;
            if (count > 0) {
                daysWithData++;
            }
        }
        String rateLabel = "0%";
        long denom = headcount * daysWithData;
        if (denom > 0) {
            long pct = Math.round((presentSum * 100.0) / denom);
            pct = Math.max(0, Math.min(100, pct));
            rateLabel = pct + "%";
        }

        return new DashboardResponse.Week(WEEK_LABELS, present, headcount, rateLabel);
    }

    private List<DashboardResponse.Activity> buildActivity(UUID companyId) {
        PageRequest top5 = PageRequest.of(0, 5);

        record Item(Instant at, DashboardResponse.Activity activity) {}
        List<Item> items = new ArrayList<>();

        for (LeaveRequest r : leaveRequests.findRecentByCompany(companyId, top5)) {
            String name = firstName(r.getEmployee());
            String text;
            String accent;
            switch (r.getStatus()) {
                case APPROVED -> { text = name + "'s leave approved"; accent = "sage"; }
                case REJECTED -> { text = name + "'s leave rejected"; accent = "coral"; }
                default -> { text = name + " requested leave"; accent = "amber"; }
            }
            items.add(new Item(r.getUpdatedAt(),
                    new DashboardResponse.Activity("leave", text, timeLabel(r.getUpdatedAt()), accent)));
        }

        for (Claim c : claims.findRecentByCompany(companyId, top5)) {
            String name = firstName(c.getEmployee());
            String text;
            String accent;
            switch (c.getStatus()) {
                case APPROVED -> { text = name + "'s claim approved"; accent = "sage"; }
                case REJECTED -> { text = name + "'s claim rejected"; accent = "coral"; }
                default -> { text = name + " submitted a claim"; accent = "amber"; }
            }
            items.add(new Item(c.getUpdatedAt(),
                    new DashboardResponse.Activity("claim", text, timeLabel(c.getUpdatedAt()), accent)));
        }

        for (OvertimeRequest o : overtime.findRecentByCompany(companyId, top5)) {
            String name = firstName(o.getEmployee());
            String text;
            String accent;
            switch (o.getStatus()) {
                case APPROVED -> { text = name + "'s overtime approved"; accent = "sage"; }
                case REJECTED -> { text = name + "'s overtime rejected"; accent = "coral"; }
                default -> { text = name + " logged overtime"; accent = "amber"; }
            }
            items.add(new Item(o.getUpdatedAt(),
                    new DashboardResponse.Activity("overtime", text, timeLabel(o.getUpdatedAt()), accent)));
        }

        return items.stream()
                .sorted((a, b) -> b.at().compareTo(a.at()))
                .limit(6)
                .map(Item::activity)
                .toList();
    }

    private static String firstName(Employee e) {
        String full = e.getFullName();
        if (full == null || full.isBlank()) {
            return "Someone";
        }
        return full.trim().split("\\s+")[0];
    }

    /** Relative time from {@code at} to now: "just now" / "Nm ago" / "Nh ago" / "Nd ago". */
    private static String timeLabel(Instant at) {
        if (at == null) {
            return "just now";
        }
        Duration d = Duration.between(at, Instant.now());
        long minutes = d.toMinutes();
        if (minutes < 1) {
            return "just now";
        }
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = d.toHours();
        if (hours < 24) {
            return hours + "h ago";
        }
        return d.toDays() + "d ago";
    }
}
