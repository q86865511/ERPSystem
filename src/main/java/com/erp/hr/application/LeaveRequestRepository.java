package com.erp.hr.application;

import com.erp.hr.api.LeaveStatus;
import com.erp.hr.domain.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    List<LeaveRequest> findByOrderByIdDesc();

    List<LeaveRequest> findByStatusOrderByIdDesc(LeaveStatus status);

    List<LeaveRequest> findByEmployeeIdOrderByIdDesc(Long employeeId);

    List<LeaveRequest> findByEmployeeIdAndStatusOrderByIdDesc(Long employeeId, LeaveStatus status);
}
