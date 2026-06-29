import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import { qk } from '../../api/queryKeys';

export type AuditFilters = { eventType?: string; actor?: string };

/**
 * Paged audit list (ADMIN-only endpoint). Spring binds {@code page}/{@code size}/{@code sort} as flat
 * query params, but openapi-fetch serializes the typed `pageable` object as `pageable[...]` by default —
 * so we flatten it ourselves in a per-call querySerializer.
 */
export function useAuditLog(filters: AuditFilters, page: number, size = 50) {
  return useQuery({
    queryKey: qk.audit.list({ ...filters, page, size }),
    placeholderData: keepPreviousData,
    queryFn: async () => {
      const { data, error } = await api.GET('/api/audit', {
        params: {
          query: {
            eventType: filters.eventType || undefined,
            actor: filters.actor || undefined,
            pageable: { page, size, sort: ['occurredAt,desc'] },
          },
        },
        querySerializer(query) {
          const sp = new URLSearchParams();
          if (query.eventType) sp.set('eventType', query.eventType);
          if (query.actor) sp.set('actor', query.actor);
          sp.set('page', String(query.pageable?.page ?? 0));
          sp.set('size', String(query.pageable?.size ?? size));
          for (const s of query.pageable?.sort ?? []) sp.append('sort', s);
          return sp.toString();
        },
      });
      if (error) throw error;
      return data;
    },
  });
}
