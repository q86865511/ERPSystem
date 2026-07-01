package com.erp.hr.application;

import com.erp.hr.api.LeaveStatus;
import com.erp.hr.api.LeaveType;
import com.erp.hr.domain.LeaveRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Files leave requests and drives their approve/reject lifecycle. */
@Service
public class LeaveService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;

    public LeaveService(LeaveRequestRepository leaveRequestRepository,
                        EmployeeRepository employeeRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public LeaveRequest submit(Long employeeId, LeaveType leaveType, LocalDate startDate,
                              LocalDate endDate, BigDecimal days, String reason) {
        requireEmployee(employeeId);
        return leaveRequestRepository.saveAndFlush(
                new LeaveRequest(employeeId, leaveType, startDate, endDate, days, reason));
    }

    @Transactional
    public LeaveRequest approve(Long id, String actor) {
        LeaveRequest request = get(id);
        guardPending(request);
        request.approve(actor, LocalDate.now());
        return leaveRequestRepository.saveAndFlush(request);
    }

    @Transactional
    public LeaveRequest reject(Long id, String actor) {
        LeaveRequest request = get(id);
        guardPending(request);
        request.reject(actor, LocalDate.now());
        return leaveRequestRepository.saveAndFlush(request);
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> list(Long employeeId, LeaveStatus status) {
        if (employeeId != null && status != null) {
            return leaveRequestRepository.findByEmployeeIdAndStatusOrderByIdDesc(employeeId, status);
        }
        if (employeeId != null) {
            return leaveRequestRepository.findByEmployeeIdOrderByIdDesc(employeeId);
        }
        if (status != null) {
            return leaveRequestRepository.findByStatusOrderByIdDesc(status);
        }
        return leaveRequestRepository.findByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public LeaveRequest get(Long id) {
        return leaveRequestRepository.findById(id)
                .orElseThrow(() -> new HrNotFoundException("leave request with id " + id));
    }

    private void guardPending(LeaveRequest request) {
        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new HrConflictException(
                    "leave request " + request.getId() + " is not PENDING, was " + request.getStatus());
        }
    }

    private void requireEmployee(Long employeeId) {
        if (employeeId == null || !employeeRepository.existsById(employeeId)) {
            throw new HrNotFoundException("employee with id " + employeeId);
        }
    }
}
