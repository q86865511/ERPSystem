import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import { qk } from '../../api/queryKeys';

export function useReconciliation(asOf?: string) {
  return useQuery({
    queryKey: qk.reporting.reconciliation(asOf),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/reporting/reconciliation', {
        params: { query: { asOf } },
      });
      if (error) throw error;
      return data;
    },
  });
}

export function useTrialBalance(asOf?: string) {
  return useQuery({
    queryKey: qk.reporting.trialBalance(asOf),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/reporting/trial-balance', {
        params: { query: { asOf } },
      });
      if (error) throw error;
      return data;
    },
  });
}

export function useIncomeStatement(asOf?: string) {
  return useQuery({
    queryKey: qk.reporting.incomeStatement(asOf),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/reporting/income-statement', {
        params: { query: { asOf } },
      });
      if (error) throw error;
      return data;
    },
  });
}

export function useBalanceSheet(asOf?: string) {
  return useQuery({
    queryKey: qk.reporting.balanceSheet(asOf),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/reporting/balance-sheet', {
        params: { query: { asOf } },
      });
      if (error) throw error;
      return data;
    },
  });
}

export function useRevenueTrend(months = 6, asOf?: string) {
  return useQuery({
    queryKey: qk.reporting.revenueTrend(months, asOf),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/reporting/revenue-trend', {
        params: { query: { months, asOf } },
      });
      if (error) throw error;
      return data;
    },
  });
}

export function useCashFlow(months = 6, asOf?: string) {
  return useQuery({
    queryKey: qk.reporting.cashFlow(months, asOf),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/reporting/cash-flow', {
        params: { query: { months, asOf } },
      });
      if (error) throw error;
      return data;
    },
  });
}

export function useKpiSummary(asOf?: string) {
  return useQuery({
    queryKey: qk.reporting.kpiSummary(asOf),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/reporting/kpi-summary', {
        params: { query: { asOf } },
      });
      if (error) throw error;
      return data;
    },
  });
}

export function useBudgetVariance(asOf?: string) {
  return useQuery({
    queryKey: qk.reporting.budgetVariance(asOf),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/reporting/budget-variance', {
        params: { query: { asOf } },
      });
      if (error) throw error;
      return data;
    },
  });
}

export function useGeneralLedger(accountCode: string | null, asOf?: string) {
  return useQuery({
    queryKey: qk.reporting.generalLedger(accountCode ?? '', asOf),
    enabled: !!accountCode,
    queryFn: async () => {
      const { data, error } = await api.GET('/api/reporting/general-ledger/{accountCode}', {
        params: { path: { accountCode: accountCode ?? '' }, query: { asOf } },
      });
      if (error) throw error;
      return data;
    },
  });
}
