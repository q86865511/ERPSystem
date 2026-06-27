import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { JournalEntryRequest } from '../../api/types';

export function useCreateJournalEntry() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: JournalEntryRequest) => {
      const { data, error } = await api.POST('/api/ledger/journal-entries', { body });
      if (error) throw error;
      return data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['reporting'] }),
  });
}

export function useClosePeriod() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: { yearCode: string; periodNo: number }) => {
      const { data, error } = await api.POST(
        '/api/ledger/fiscal-years/{yearCode}/periods/{periodNo}/close',
        { params: { path: { yearCode: args.yearCode, periodNo: args.periodNo } } },
      );
      if (error) throw error;
      return data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['reporting'] }),
  });
}

export function useReopenPeriod() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: { yearCode: string; periodNo: number }) => {
      const { data, error } = await api.POST(
        '/api/ledger/fiscal-years/{yearCode}/periods/{periodNo}/reopen',
        { params: { path: { yearCode: args.yearCode, periodNo: args.periodNo } } },
      );
      if (error) throw error;
      return data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['reporting'] }),
  });
}
