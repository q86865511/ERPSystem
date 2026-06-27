import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/client';
import { qk } from '../../api/queryKeys';
import type { CreateBillRequest, CreateGrnRequest, CreatePoRequest } from '../../api/types';

export function useOrders() {
  return useQuery({
    queryKey: qk.purchasing.orders(),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/purchasing/purchase-orders');
      if (error) throw error;
      return data;
    },
  });
}

export function useOrder(id: number | null) {
  return useQuery({
    queryKey: qk.purchasing.order(id ?? 0),
    enabled: !!id,
    queryFn: async () => {
      const { data, error } = await api.GET('/api/purchasing/purchase-orders/{id}', {
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
    mutationFn: async (body: CreatePoRequest) => {
      const { data, error } = await api.POST('/api/purchasing/purchase-orders', { body });
      if (error) throw error;
      return data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['purchasing'] }),
  });
}

export function useConfirmOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (id: number) => {
      const { data, error } = await api.POST('/api/purchasing/purchase-orders/{id}/confirm', {
        params: { path: { id } },
      });
      if (error) throw error;
      return data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['purchasing'] }),
  });
}

export function useReceipts() {
  return useQuery({
    queryKey: qk.purchasing.receipts(),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/purchasing/goods-receipts');
      if (error) throw error;
      return data;
    },
  });
}

export function useReceipt(id: number | null) {
  return useQuery({
    queryKey: ['purchasing', 'receipt', id ?? 0],
    enabled: !!id,
    queryFn: async () => {
      const { data, error } = await api.GET('/api/purchasing/goods-receipts/{id}', {
        params: { path: { id: id ?? 0 } },
      });
      if (error) throw error;
      return data;
    },
  });
}

export function useCreateReceipt() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreateGrnRequest) => {
      const { data, error } = await api.POST('/api/purchasing/goods-receipts', { body });
      if (error) throw error;
      return data;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['purchasing'] });
      qc.invalidateQueries({ queryKey: ['inventory'] });
      qc.invalidateQueries({ queryKey: ['reporting'] });
    },
  });
}

export function useBills() {
  return useQuery({
    queryKey: qk.purchasing.bills(),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/purchasing/vendor-bills');
      if (error) throw error;
      return data;
    },
  });
}

export function useBill(id: number | null) {
  return useQuery({
    queryKey: ['purchasing', 'bill', id ?? 0],
    enabled: !!id,
    queryFn: async () => {
      const { data, error } = await api.GET('/api/purchasing/vendor-bills/{id}', {
        params: { path: { id: id ?? 0 } },
      });
      if (error) throw error;
      return data;
    },
  });
}

export function useCreateBill() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreateBillRequest) => {
      const { data, error } = await api.POST('/api/purchasing/vendor-bills', { body });
      if (error) throw error;
      return data;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['purchasing'] });
      qc.invalidateQueries({ queryKey: ['inventory'] });
      qc.invalidateQueries({ queryKey: ['reporting'] });
    },
  });
}

export function useApAging(asOf?: string) {
  return useQuery({
    queryKey: qk.purchasing.apAging(asOf),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/purchasing/ap-aging', {
        params: { query: { asOf } },
      });
      if (error) throw error;
      return data;
    },
  });
}
