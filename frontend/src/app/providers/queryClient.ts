import { QueryClient } from '@tanstack/react-query';
import { ApiError } from '@/api/client';
import { STALE_TIME } from '@/lib/queryKeys';

/**
 * Server state lives in TanStack Query. IPL data changes only after an ingestion, so responses stay fresh for
 * minutes and are not refetched on window focus. Client errors (400/404/422) are never retried: retrying cannot
 * fix them. Server and network errors are retried once.
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: STALE_TIME.statistics,
        gcTime: 30 * 60 * 1000,
        refetchOnWindowFocus: false,
        retry: (failureCount, error) =>
          !(error instanceof ApiError && error.status >= 400 && error.status < 500) && failureCount < 1,
      },
    },
  });
}
