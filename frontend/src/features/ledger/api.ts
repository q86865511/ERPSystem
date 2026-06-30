import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { JournalEntryRequest } from '../../api/types';

/** Full detail of one journal entry by its business number (header + lines + reversal links). */
export function useJournalEntry(entryNo: number | null) {
  return useQuery({
    queryKey: ['ledger', 'entry', entryNo],
    enabled: entryNo != null,
    queryFn: async () => {
      const { data, error } = await api.GET('/api/ledger/journal-entries/{entryNo}', {
        params: { path: { entryNo: entryNo as number } },
      });
      if (error) throw error;
      return data;
    },
  });
}

export function useReverseEntry() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: { entryNo: number; reversalDate?: string; memo?: string }) => {
      const { data, error } = await api.POST('/api/ledger/journal-entries/{entryNo}/reverse', {
        params: { path: { entryNo: args.entryNo } },
        body: { reversalDate: args.reversalDate, memo: args.memo },
      });
      if (error) throw error;
      return data;
    },
    onSuccess: (_data, args) => {
      qc.invalidateQueries({ queryKey: ['reporting'] });
      qc.invalidateQueries({ queryKey: ['ledger', 'entry', args.entryNo] });
    },
  });
}

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

export function useCloseYear() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (yearCode: string) => {
      const { data, error } = await api.POST('/api/ledger/fiscal-years/{yearCode}/close-year', {
        params: { path: { yearCode } },
      });
      if (error) throw error;
      return data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['reporting'] }),
  });
}
