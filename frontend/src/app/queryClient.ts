import { QueryClient } from '@tanstack/react-query';

/** ERP data changes deliberately (via postings), so reads are cached briefly and not refetched on focus. */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      gcTime: 5 * 60_000,
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
});
