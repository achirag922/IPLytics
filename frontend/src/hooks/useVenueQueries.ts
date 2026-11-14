import { useQuery } from '@tanstack/react-query';
import { venuesApi, type VenueStatsQuery } from '@/api/venuesApi';
import { queryKeys, STALE_TIME } from '@/lib/queryKeys';

export function useVenuesQuery() {
  return useQuery({
    queryKey: queryKeys.venues.all(),
    queryFn: ({ signal }) => venuesApi.list(signal),
    staleTime: STALE_TIME.reference,
  });
}

export function useVenueQuery(id: number | undefined) {
  return useQuery({
    queryKey: queryKeys.venues.detail(id ?? 0),
    queryFn: ({ signal }) => venuesApi.get(id as number, signal),
    enabled: id !== undefined,
    staleTime: STALE_TIME.reference,
  });
}

export function useVenueStatsQuery(id: number | undefined, query: VenueStatsQuery = {}) {
  return useQuery({
    queryKey: queryKeys.venues.resource(id ?? 0, 'stats', query),
    queryFn: ({ signal }) => venuesApi.stats(id as number, query, signal),
    enabled: id !== undefined,
  });
}

export function useVenueSeasonsQuery(id: number | undefined, query: VenueStatsQuery = {}) {
  return useQuery({
    queryKey: queryKeys.venues.resource(id ?? 0, 'seasons', query),
    queryFn: ({ signal }) => venuesApi.seasons(id as number, query, signal),
    enabled: id !== undefined,
  });
}

export function useVenueScoreDistributionQuery(id: number | undefined, query: VenueStatsQuery = {}) {
  return useQuery({
    queryKey: queryKeys.venues.resource(id ?? 0, 'score-distribution', query),
    queryFn: ({ signal }) => venuesApi.scoreDistribution(id as number, query, signal),
    enabled: id !== undefined,
  });
}
