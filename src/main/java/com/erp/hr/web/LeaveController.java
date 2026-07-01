package com.erp.hr.web;

import com.erp.hr.api.LeaveStatus;
import com.erp.hr.api.LeaveType;
import com.erp.hr.application.LeaveService;
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
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

/** REST surface for leave requests and their approve/reject lifecycle. */
@RestController
@RequestMapping("/api/hr/leave-requests")
public class LeaveController {

    private final LeaveService leaveService;

    public LeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    public record CreateLeaveRequest(Long employeeId, LeaveType leaveType, LocalDate startDate,
                                     LocalDate endDate, BigDecimal days, String reason) {
    }

    @PostMapping
    public ResponseEntity<LeaveRequestResponse> submit(@RequestBody CreateLeaveRequest request) {
        LeaveRequestResponse body = LeaveRequestResponse.from(leaveService.submit(request.employeeId(),
                request.leaveType(), request.startDate(), request.endDate(), request.days(),
                request.reason()));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/{id}/approve")
    public LeaveRequestResponse approve(@PathVariable Long id, Principal principal) {
        return LeaveRequestResponse.from(leaveService.approve(id, actor(principal)));
    }

    @PostMapping("/{id}/reject")
    public LeaveRequestResponse reject(@PathVariable Long id, Principal principal) {
        return LeaveRequestResponse.from(leaveService.reject(id, actor(principal)));
    }

    @GetMapping
    public List<LeaveRequestResponse> list(@RequestParam(required = false) Long employeeId,
                                           @RequestParam(required = false) LeaveStatus status) {
        return leaveService.list(employeeId, status).stream().map(LeaveRequestResponse::from).toList();
    }

    private static String actor(Principal principal) {
        return principal != null ? principal.getName() : "system";
    }
}
