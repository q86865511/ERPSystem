import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/client';
import { qk } from '../../api/queryKeys';
import type {
  CreateItemRequest,
  CreateLocationRequest,
  CreatePartnerRequest,
  CreateWarehouseRequest,
} from '../../api/types';

// --- Items ---------------------------------------------------------------------------------------
export function useItems() {
  return useQuery({
    queryKey: qk.masterdata.items(),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/masterdata/items');
      if (error) throw error;
      return data;
    },
  });
}

export function useCreateItem() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreateItemRequest) => {
      const { data, error } = await api.POST('/api/masterdata/items', { body });
      if (error) throw error;
      return data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.masterdata.items() }),
  });
}

// --- Partners ------------------------------------------------------------------------------------
export function usePartners(filter?: { vendor?: boolean; customer?: boolean }) {
  return useQuery({
    queryKey: qk.masterdata.partners(filter),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/masterdata/partners', {
        params: { query: { vendor: filter?.vendor, customer: filter?.customer } },
      });
      if (error) throw error;
      return data;
    },
  });
}

export function useCreatePartner() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreatePartnerRequest) => {
      const { data, error } = await api.POST('/api/masterdata/partners', { body });
      if (error) throw error;
      return data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['masterdata', 'partners'] }),
  });
}

// --- Warehouses ----------------------------------------------------------------------------------
export function useWarehouses() {
  return useQuery({
    queryKey: qk.masterdata.warehouses(),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/masterdata/warehouses');
      if (error) throw error;
      return data;
    },
  });
}

export function useCreateWarehouse() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreateWarehouseRequest) => {
      const { data, error } = await api.POST('/api/masterdata/warehouses', { body });
      if (error) throw error;
      return data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: qk.masterdata.warehouses() }),
  });
}

// --- Locations -----------------------------------------------------------------------------------
export function useLocations(warehouseId?: number) {
  return useQuery({
    queryKey: qk.masterdata.locations(warehouseId),
    queryFn: async () => {
      const { data, error } = await api.GET('/api/masterdata/locations', {
        params: { query: { warehouseId } },
      });
      if (error) throw error;
      return data;
    },
  });
}

export function useCreateLocation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreateLocationRequest) => {
      const { data, error } = await api.POST('/api/masterdata/locations', { body });
      if (error) throw error;
      return data;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: ['masterdata', 'locations'] }),
  });
}
