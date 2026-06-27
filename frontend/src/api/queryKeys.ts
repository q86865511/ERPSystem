/** Central query-key factory so cache invalidation stays consistent across the app. */
export const qk = {
  auth: {
    me: () => ['auth', 'me'] as const,
  },
  masterdata: {
    items: () => ['masterdata', 'items'] as const,
    item: (id: number) => ['masterdata', 'item', id] as const,
    partners: (filter?: { vendor?: boolean; customer?: boolean }) =>
      ['masterdata', 'partners', filter ?? {}] as const,
    warehouses: () => ['masterdata', 'warehouses'] as const,
    locations: (warehouseId?: number) =>
      ['masterdata', 'locations', warehouseId ?? 'all'] as const,
  },
  reporting: {
    reconciliation: (asOf?: string) => ['reporting', 'reconciliation', asOf ?? 'today'] as const,
    trialBalance: (asOf?: string) => ['reporting', 'trial-balance', asOf ?? 'today'] as const,
    incomeStatement: (asOf?: string) => ['reporting', 'income-statement', asOf ?? 'today'] as const,
    balanceSheet: (asOf?: string) => ['reporting', 'balance-sheet', asOf ?? 'today'] as const,
    generalLedger: (accountCode: string, asOf?: string) =>
      ['reporting', 'general-ledger', accountCode, asOf ?? 'today'] as const,
  },
  inventory: {
    reconciliation: () => ['inventory', 'reconciliation'] as const,
    onHand: (itemId: number) => ['inventory', 'on-hand', itemId] as const,
  },
};
