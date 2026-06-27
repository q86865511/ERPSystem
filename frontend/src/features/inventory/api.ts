import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import { qk } from '../../api/queryKeys';

export function useInventoryReconciliation() {
  return useQuery({
    queryKey: qk.inventory.reconciliation(),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/inventory/reconciliation');
      if (error) throw error;
      return data;
    },
  });
}

export function useItemOnHand(itemId: number | null) {
  return useQuery({
    queryKey: qk.inventory.onHand(itemId ?? 0),
    enabled: !!itemId,
    queryFn: async () => {
      const { data, error } = await api.GET('/api/inventory/items/{itemId}/on-hand', {
        params: { path: { itemId: itemId ?? 0 } },
      });
      if (error) throw error;
      return data;
    },
  });
}
