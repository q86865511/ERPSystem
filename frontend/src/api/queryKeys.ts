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
  purchasing: {
    orders: () => ['purchasing', 'orders'] as const,
    order: (id: number) => ['purchasing', 'order', id] as const,
    receipts: () => ['purchasing', 'receipts'] as const,
    bills: () => ['purchasing', 'bills'] as const,
    apAging: (asOf?: string) => ['purchasing', 'ap-aging', asOf ?? 'today'] as const,
  },
  payments: {
    list: (direction?: string) => ['payments', 'list', direction ?? 'all'] as const,
    one: (id: number) => ['payments', 'one', id] as const,
  },
  sales: {
    orders: () => ['sales', 'orders'] as const,
    order: (id: number) => ['sales', 'order', id] as const,
    deliveries: () => ['sales', 'deliveries'] as const,
    invoices: () => ['sales', 'invoices'] as const,
    returns: () => ['sales', 'returns'] as const,
    arAging: (asOf?: string) => ['sales', 'ar-aging', asOf ?? 'today'] as const,
  },
  manufacturing: {
    boms: () => ['manufacturing', 'boms'] as const,
    workOrders: () => ['manufacturing', 'work-orders'] as const,
    workOrder: (id: number) => ['manufacturing', 'work-order', id] as const,
    reorder: () => ['manufacturing', 'reorder'] as const,
  },
  audit: {
    list: (params: { eventType?: string; actor?: string; page: number; size: number }) =>
      ['audit', 'list', params] as const,
  },
};
