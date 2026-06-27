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
};
