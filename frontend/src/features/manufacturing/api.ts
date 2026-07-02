import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/client';
import { qk } from '../../api/queryKeys';
import type { CreateBomRequest, CreateWorkOrderRequest } from '../../api/types';

const invalidateMfg = (qc: ReturnType<typeof useQueryClient>, posting = false) => {
  qc.invalidateQueries({ queryKey: ['manufacturing'] });
  if (posting) {
    qc.invalidateQueries({ queryKey: ['inventory'] });
    qc.invalidateQueries({ queryKey: ['reporting'] });
  }
};

// --- BOMs ---
export function useBoms() {
  return useQuery({
    queryKey: qk.manufacturing.boms(),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/manufacturing/boms');
      if (error) throw error;
      return data;
    },
  });
}

export function useCreateBom() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreateBomRequest) => {
      const { data, error } = await api.POST('/api/manufacturing/boms', { body });
      if (error) throw error;
      return data;
    },
    onSuccess: () => invalidateMfg(qc),
  });
}

// --- Work orders ---
export function useWorkOrders() {
  return useQuery({
    queryKey: qk.manufacturing.workOrders(),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/manufacturing/work-orders');
      if (error) throw error;
      return data;
    },
  });
}

export function useOee() {
  return useQuery({
    queryKey: qk.manufacturing.oee(),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/manufacturing/oee');
      if (error) throw error;
      return data;
    },
  });
}

export function useDowntime() {
  return useQuery({
    queryKey: qk.manufacturing.downtime(),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/manufacturing/downtime');
      if (error) throw error;
      return data;
    },
  });
}

export function useWorkOrder(id: number | null) {
  return useQuery({
    queryKey: qk.manufacturing.workOrder(id ?? 0),
    enabled: !!id,
    queryFn: async () => {
      const { data, error } = await api.GET('/api/manufacturing/work-orders/{id}', {
        params: { path: { id: id ?? 0 } },
      });
      if (error) throw error;
      return data;
    },
  });
}

export function useCreateWorkOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreateWorkOrderRequest) => {
      const { data, error } = await api.POST('/api/manufacturing/work-orders', { body });
      if (error) throw error;
      return data;
    },
    onSuccess: () => invalidateMfg(qc),
  });
}

export function useReleaseWorkOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (id: number) => {
      const { data, error } = await api.POST('/api/manufacturing/work-orders/{id}/release', {
        params: { path: { id } },
      });
      if (error) throw error;
      return data;
    },
    onSuccess: () => invalidateMfg(qc),
  });
}

export function useIssueWorkOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: { id: number; stockLocationId: number; postingDate?: string }) => {
      const { data, error } = await api.POST('/api/manufacturing/work-orders/{id}/issue', {
        params: { path: { id: args.id } },
        body: { stockLocationId: args.stockLocationId, postingDate: args.postingDate },
      });
      if (error) throw error;
      return data;
    },
    onSuccess: () => invalidateMfg(qc, true),
  });
}

export function useCompleteWorkOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: {
      id: number;
      qtyProduced: string;
      stockLocationId: number;
      postingDate?: string;
    }) => {
      const { data, error } = await api.POST('/api/manufacturing/work-orders/{id}/complete', {
        params: { path: { id: args.id } },
        body: {
          qtyProduced: args.qtyProduced,
          stockLocationId: args.stockLocationId,
          postingDate: args.postingDate,
        },
      });
      if (error) throw error;
      return data;
    },
    onSuccess: () => invalidateMfg(qc, true),
  });
}

export function useCancelWorkOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: { id: number; stockLocationId: number; postingDate?: string }) => {
      const { data, error } = await api.POST('/api/manufacturing/work-orders/{id}/cancel', {
        params: { path: { id: args.id } },
        body: { stockLocationId: args.stockLocationId, postingDate: args.postingDate },
      });
      if (error) throw error;
      return data;
    },
    onSuccess: () => invalidateMfg(qc, true),
  });
}

export function useReorderReport() {
  return useQuery({
    queryKey: qk.manufacturing.reorder(),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/manufacturing/reorder-report');
      if (error) throw error;
      return data;
    },
  });
}
