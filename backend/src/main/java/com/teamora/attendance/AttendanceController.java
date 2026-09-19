package com.teamora.attendance;

import com.teamora.attendance.dto.AttendanceSummaryResponse;
import com.teamora.attendance.dto.LiveAttendanceResponse;
import com.teamora.attendance.dto.TodayStatusResponse;
import com.teamora.security.CurrentEmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final CurrentEmployeeService currentEmployee;

    /** Clock in for today. */
    @PostMapping("/api/attendance/clock-in")
    public TodayStatusResponse clockIn() {
        return attendanceService.clockIn(currentEmployee.require());
    }

    /** Clock out for today. */
    @PostMapping("/api/attendance/clock-out")
    public TodayStatusResponse clockOut() {
        return attendanceService.clockOut(currentEmployee.require());
    }

    /** Today's status for the Home hero. */
    @GetMapping("/api/attendance/today")
    public TodayStatusResponse today() {
        return attendanceService.today(currentEmployee.require());
    }

    /** Monthly attendance summary + history for the staff Attendance screen. */
    @GetMapping("/api/attendance/me")
    public AttendanceSummaryResponse myHistory(@RequestParam(required = false) String month) {
        return attendanceService.myHistory(currentEmployee.require(), month);
    }

    /** Admin Live board — restricted to ADMIN by SecurityConfig (/api/admin/**). */
    @GetMapping("/api/admin/attendance/live")
    public LiveAttendanceResponse live() {
        return attendanceService.liveBoard(currentEmployee.require().getCompany().getId());
    }
}
