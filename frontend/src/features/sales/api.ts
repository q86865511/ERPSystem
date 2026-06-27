import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/client';
import { qk } from '../../api/queryKeys';
import type {
  CreateDeliveryRequest,
  CreateInvoiceRequest,
  CreateReturnRequest,
  CreateSoRequest,
} from '../../api/types';

const invalidate = (qc: ReturnType<typeof useQueryClient>, ...keys: string[]) =>
  keys.forEach((k) => qc.invalidateQueries({ queryKey: [k] }));

// --- Sales orders ---
export function useOrders() {
  return useQuery({
    queryKey: qk.sales.orders(),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/sales/sales-orders');
      if (error) throw error;
      return data;
    },
  });
}

export function useOrder(id: number | null) {
  return useQuery({
    queryKey: qk.sales.order(id ?? 0),
    enabled: !!id,
    queryFn: async () => {
      const { data, error } = await api.GET('/api/sales/sales-orders/{id}', {
        params: { path: { id: id ?? 0 } },
      });
      if (error) throw error;
      return data;
    },
  });
}

export function useCreateOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreateSoRequest) => {
      const { data, error } = await api.POST('/api/sales/sales-orders', { body });
      if (error) throw error;
      return data;
    },
    onSuccess: () => invalidate(qc, 'sales'),
  });
}

export function useConfirmOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (id: number) => {
      const { data, error } = await api.POST('/api/sales/sales-orders/{id}/confirm', {
        params: { path: { id } },
      });
      if (error) throw error;
      return data;
    },
    onSuccess: () => invalidate(qc, 'sales'),
  });
}

// --- Deliveries ---
export function useDeliveries() {
  return useQuery({
    queryKey: qk.sales.deliveries(),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/sales/deliveries');
      if (error) throw error;
      return data;
    },
  });
}

export function useDelivery(id: number | null) {
  return useQuery({
    queryKey: ['sales', 'delivery', id ?? 0],
    enabled: !!id,
    queryFn: async () => {
      const { data, error } = await api.GET('/api/sales/deliveries/{id}', {
        params: { path: { id: id ?? 0 } },
      });
      if (error) throw error;
      return data;
    },
  });
}

export function useCreateDelivery() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreateDeliveryRequest) => {
      const { data, error } = await api.POST('/api/sales/deliveries', { body });
      if (error) throw error;
      return data;
    },
    onSuccess: () => invalidate(qc, 'sales', 'inventory', 'reporting'),
  });
}

// --- Invoices ---
export function useInvoices() {
  return useQuery({
    queryKey: qk.sales.invoices(),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/sales/sales-invoices');
      if (error) throw error;
      return data;
    },
  });
}

export function useInvoice(id: number | null) {
  return useQuery({
    queryKey: ['sales', 'invoice', id ?? 0],
    enabled: !!id,
    queryFn: async () => {
      const { data, error } = await api.GET('/api/sales/sales-invoices/{id}', {
        params: { path: { id: id ?? 0 } },
      });
      if (error) throw error;
      return data;
    },
  });
}

export function useCreateInvoice() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreateInvoiceRequest) => {
      const { data, error } = await api.POST('/api/sales/sales-invoices', { body });
      if (error) throw error;
      return data;
    },
    onSuccess: () => invalidate(qc, 'sales', 'inventory', 'reporting'),
  });
}

// --- Customer returns ---
export function useReturns() {
  return useQuery({
    queryKey: qk.sales.returns(),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/sales/customer-returns');
      if (error) throw error;
      return data;
    },
  });
}

export function useReturn(id: number | null) {
  return useQuery({
    queryKey: ['sales', 'return', id ?? 0],
    enabled: !!id,
    queryFn: async () => {
      const { data, error } = await api.GET('/api/sales/customer-returns/{id}', {
        params: { path: { id: id ?? 0 } },
      });
      if (error) throw error;
      return data;
    },
  });
}

export function useCreateReturn() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreateReturnRequest) => {
      const { data, error } = await api.POST('/api/sales/customer-returns', { body });
      if (error) throw error;
      return data;
    },
    onSuccess: () => invalidate(qc, 'sales', 'inventory', 'reporting'),
  });
}

export function useArAging(asOf?: string) {
  return useQuery({
    queryKey: qk.sales.arAging(asOf),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/sales/ar-aging', { params: { query: { asOf } } });
      if (error) throw error;
      return data;
    },
  });
}
