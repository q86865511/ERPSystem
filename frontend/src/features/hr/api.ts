import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/client';
import { qk } from '../../api/queryKeys';
import type {
  CreateAttendanceRequest,
  CreateDepartmentRequest,
  CreateEmployeeRequest,
  CreateLeaveRequest,
  CreatePositionRequest,
  CreateTimesheetRequest,
  LeaveStatus,
  TimesheetStatus,
} from '../../api/types';

// --- Departments ---------------------------------------------------------------------------------
export function useDepartments() {
  return useQuery({
    queryKey: qk.hr.departments(),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/hr/departments');
      if (error) throw error;
      return data;
    },
  });
}

export function useCreateDepartment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreateDepartmentRequest) => {
      const { data, error } = await api.POST('/api/hr/departments', { body });
      if (error) throw error;
      return data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['hr', 'departments'] }),
  });
}

// --- Positions -----------------------------------------------------------------------------------
export function usePositions() {
  return useQuery({
    queryKey: qk.hr.positions(),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/hr/positions');
      if (error) throw error;
      return data;
    },
  });
}

export function useCreatePosition() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreatePositionRequest) => {
      const { data, error } = await api.POST('/api/hr/positions', { body });
      if (error) throw error;
      return data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['hr', 'positions'] }),
  });
}

// --- Employees -----------------------------------------------------------------------------------
export function useEmployees() {
  return useQuery({
    queryKey: qk.hr.employees(),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/hr/employees');
      if (error) throw error;
      return data;
    },
  });
}

export function useCreateEmployee() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreateEmployeeRequest) => {
      const { data, error } = await api.POST('/api/hr/employees', { body });
      if (error) throw error;
      return data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['hr', 'employees'] }),
  });
}

// --- Attendance (B2) -----------------------------------------------------------------------------
export function useAttendance(employeeId?: number, month?: string) {
  return useQuery({
    queryKey: qk.hr.attendance(employeeId, month),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/hr/attendance', {
        params: { query: { employeeId, month } },
      });
      if (error) throw error;
      return data;
    },
  });
}

export function useRecordAttendance() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreateAttendanceRequest) => {
      const { data, error } = await api.POST('/api/hr/attendance', { body });
      if (error) throw error;
      return data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['hr', 'attendance'] }),
  });
}

// --- Leave requests (B2) -------------------------------------------------------------------------
export function useLeaveRequests(status?: LeaveStatus) {
  return useQuery({
    queryKey: qk.hr.leaveRequests(status),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/hr/leave-requests', {
        params: { query: { status } },
      });
      if (error) throw error;
      return data;
    },
  });
}

export function useSubmitLeave() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreateLeaveRequest) => {
      const { data, error } = await api.POST('/api/hr/leave-requests', { body });
      if (error) throw error;
      return data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['hr', 'leave-requests'] }),
  });
}

export function useDecideLeave(decision: 'approve' | 'reject') {
  const qc = useQueryClient();
  const path =
    decision === 'approve'
      ? ('/api/hr/leave-requests/{id}/approve' as const)
      : ('/api/hr/leave-requests/{id}/reject' as const);
  return useMutation({
    mutationFn: async (id: number) => {
      const { data, error } = await api.POST(path, { params: { path: { id } } });
      if (error) throw error;
      return data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['hr', 'leave-requests'] }),
  });
}

// --- Timesheets (B2) -----------------------------------------------------------------------------
export function useTimesheets(status?: TimesheetStatus) {
  return useQuery({
    queryKey: qk.hr.timesheets(status),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/hr/timesheets', {
        params: { query: { status } },
      });
      if (error) throw error;
      return data;
    },
  });
}

export function useCreateTimesheet() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreateTimesheetRequest) => {
      const { data, error } = await api.POST('/api/hr/timesheets', { body });
      if (error) throw error;
      return data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['hr', 'timesheets'] }),
  });
}

export function useAdvanceTimesheet(action: 'submit' | 'approve') {
  const qc = useQueryClient();
  const path =
    action === 'submit'
      ? ('/api/hr/timesheets/{id}/submit' as const)
      : ('/api/hr/timesheets/{id}/approve' as const);
  return useMutation({
    mutationFn: async (id: number) => {
      const { data, error } = await api.POST(path, { params: { path: { id } } });
      if (error) throw error;
      return data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['hr', 'timesheets'] }),
  });
}
