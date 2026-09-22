package com.teamora.attendance;

import com.teamora.attendance.dto.AttendanceSummaryResponse;
import com.teamora.attendance.dto.ClockInRequest;
import com.teamora.attendance.dto.LiveAttendanceResponse;
import com.teamora.attendance.dto.TodayStatusResponse;
import com.teamora.attendance.AttendanceService.PhotoData;
import com.teamora.security.CurrentEmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final CurrentEmployeeService currentEmployee;

    /** Clock in for today. Body is optional; coords are enforced only for geofenced staff. */
    @PostMapping("/api/attendance/clock-in")
    public TodayStatusResponse clockIn(@Valid @RequestBody(required = false) ClockInRequest req) {
        return attendanceService.clockIn(
                currentEmployee.require(),
                req != null ? req.latitude() : null,
                req != null ? req.longitude() : null,
                req != null ? req.photoBase64() : null);
    }

    /**
     * Stream a clock-in selfie. Access: the record's own employee or a
     * same-company admin (OWNER/HR_ADMIN/MANAGER); 403 otherwise, 404 if the
     * record or its photo is missing. Enforced in the service (this path is not
     * under /api/admin/**).
     */
    @GetMapping("/api/attendance/records/{id}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable UUID id) {
        PhotoData photo = attendanceService.getPhoto(currentEmployee.require(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate())
                .body(photo.bytes());
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
