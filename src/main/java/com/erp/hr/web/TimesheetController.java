package com.erp.hr.web;

import com.erp.hr.api.TimesheetStatus;
import com.erp.hr.application.TimesheetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** REST surface for weekly timesheets and their draft → submitted → approved lifecycle. */
@RestController
@RequestMapping("/api/hr/timesheets")
public class TimesheetController {

    private final TimesheetService timesheetService;

    public TimesheetController(TimesheetService timesheetService) {
        this.timesheetService = timesheetService;
    }

    public record CreateTimesheetRequest(Long employeeId, LocalDate weekEnding, BigDecimal regularHours,
                                         BigDecimal overtimeHours, String note) {
    }

    @PostMapping
    public ResponseEntity<TimesheetResponse> create(@RequestBody CreateTimesheetRequest request) {
        TimesheetResponse body = TimesheetResponse.from(timesheetService.create(request.employeeId(),
                request.weekEnding(), request.regularHours(), request.overtimeHours(), request.note()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/{id}/submit")
    public TimesheetResponse submit(@PathVariable Long id) {
        return TimesheetResponse.from(timesheetService.submit(id));
    }

    @PostMapping("/{id}/approve")
    public TimesheetResponse approve(@PathVariable Long id) {
        return TimesheetResponse.from(timesheetService.approve(id));
    }

    @GetMapping
    public List<TimesheetResponse> list(@RequestParam(required = false) Long employeeId,
                                        @RequestParam(required = false) TimesheetStatus status) {
        return timesheetService.list(employeeId, status).stream().map(TimesheetResponse::from).toList();
    }
}
