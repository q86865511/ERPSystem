import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/client';
import { qk } from '../../api/queryKeys';
import type {
  CreateDepartmentRequest,
  CreateEmployeeRequest,
  CreatePositionRequest,
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
