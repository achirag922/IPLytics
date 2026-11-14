import { useQuery } from '@tanstack/react-query';
import { seasonsApi, type SeasonSummaryQuery } from '@/api/seasonsApi';
import { queryKeys, STALE_TIME } from '@/lib/queryKeys';

export function useSeasonsQuery() {
  return useQuery({
    queryKey: queryKeys.seasons.all(),
    queryFn: ({ signal }) => seasonsApi.list(signal),
    staleTime: STALE_TIME.reference,
  });
}

export function useSeasonSummaryQuery(year: number | undefined, query: SeasonSummaryQuery = {}) {
  return useQuery({
    queryKey: queryKeys.seasons.summary(year ?? 0, query),
    queryFn: ({ signal }) => seasonsApi.summary(year as number, query, signal),
    enabled: year !== undefined,
  });
}

export function usePointsTableQuery(year: number | undefined) {
  return useQuery({
    queryKey: queryKeys.seasons.pointsTable(year ?? 0),
    queryFn: ({ signal }) => seasonsApi.pointsTable(year as number, signal),
    enabled: year !== undefined,
  });
}
