import { QueryClient } from '@tanstack/react-query';

/** IPL data only changes when an ingestion runs, so results can stay fresh for a while. */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 5 * 60 * 1000,
        retry: 1,
        refetchOnWindowFocus: false,
      },
    },
  });
}
