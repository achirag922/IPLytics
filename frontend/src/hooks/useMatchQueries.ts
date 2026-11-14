import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { matchesApi, type MatchListQuery } from '@/api/matchesApi';
import { queryKeys, STALE_TIME } from '@/lib/queryKeys';

export function useMatchesQuery(query: MatchListQuery | undefined) {
  return useQuery({
    queryKey: queryKeys.matches.list(query ?? {}),
    queryFn: ({ signal }) => matchesApi.list(query ?? {}, signal),
    enabled: query !== undefined,
    placeholderData: keepPreviousData,
  });
}

/** A finished match never changes, so its detail, scorecard and progression are cached for long. */
export function useMatchQuery(id: number | undefined) {
  return useQuery({
    queryKey: queryKeys.matches.detail(id ?? 0),
    queryFn: ({ signal }) => matchesApi.get(id as number, signal),
    enabled: id !== undefined,
    staleTime: STALE_TIME.reference,
  });
}

export function useScorecardQuery(id: number | undefined) {
  return useQuery({
    queryKey: queryKeys.matches.resource(id ?? 0, 'scorecard'),
    queryFn: ({ signal }) => matchesApi.scorecard(id as number, signal),
    enabled: id !== undefined,
    staleTime: STALE_TIME.reference,
  });
}

export function useMatchProgressionQuery(id: number | undefined) {
  return useQuery({
    queryKey: queryKeys.matches.resource(id ?? 0, 'progression'),
    queryFn: ({ signal }) => matchesApi.progression(id as number, signal),
    enabled: id !== undefined,
    staleTime: STALE_TIME.reference,
  });
}
