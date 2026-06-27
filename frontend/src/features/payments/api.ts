import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/client';
import { qk } from '../../api/queryKeys';
import type { PayInRequest, PayOutRequest } from '../../api/types';

export type PaymentDirection = 'IN' | 'OUT';

export function usePayments(direction?: PaymentDirection) {
  return useQuery({
    queryKey: qk.payments.list(direction),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/payments', { params: { query: { direction } } });
      if (error) throw error;
      return data;
    },
  });
}

export function usePayment(id: number | null) {
  return useQuery({
    queryKey: qk.payments.one(id ?? 0),
    enabled: !!id,
    queryFn: async () => {
      const { data, error } = await api.GET('/api/payments/{id}', { params: { path: { id: id ?? 0 } } });
      if (error) throw error;
      return data;
    },
  });
}

export function usePayOut() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: PayOutRequest) => {
      const { data, error } = await api.POST('/api/payments/out', { body });
      if (error) throw error;
      return data;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['payments'] });
      qc.invalidateQueries({ queryKey: ['purchasing'] });
      qc.invalidateQueries({ queryKey: ['reporting'] });
    },
  });
}

export function usePayIn() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: PayInRequest) => {
      const { data, error } = await api.POST('/api/payments/in', { body });
      if (error) throw error;
      return data;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['payments'] });
      qc.invalidateQueries({ queryKey: ['sales'] });
      qc.invalidateQueries({ queryKey: ['reporting'] });
    },
  });
}
