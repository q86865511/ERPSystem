package com.erp.hr.web;

import com.erp.hr.api.AttendanceStatus;
import com.erp.hr.application.AttendanceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** REST surface for daily attendance records. */
@RestController
@RequestMapping("/api/hr/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    public record CreateAttendanceRequest(@NotNull Long employeeId, @NotNull LocalDate workDate,
                                          @NotNull AttendanceStatus status, BigDecimal workedHours,
                                          String note) {
    }

    @PostMapping
    public ResponseEntity<AttendanceResponse> record(@Valid @RequestBody CreateAttendanceRequest request) {
        AttendanceResponse body = AttendanceResponse.from(attendanceService.record(request.employeeId(),
                request.workDate(), request.status(), request.workedHours(), request.note()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping
    public List<AttendanceResponse> list(@RequestParam(required = false) Long employeeId,
                                         @RequestParam(required = false)
                                         @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return attendanceService.list(employeeId, month).stream().map(AttendanceResponse::from).toList();
    }
}
