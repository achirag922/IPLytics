import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { analyticsApi, type InsightQuery, type SeasonComparisonQuery } from '@/api/analyticsApi';
import { playersApi, type PlayerStatsQuery } from '@/api/playersApi';
import { venuesApi, type VenueStatsQuery } from '@/api/venuesApi';
import { queryKeys } from '@/lib/queryKeys';

/**
 * Insight queries (V1.6). Filters arrive resolved (team codes -> ids); an undefined query waits. Previous data stays
 * on screen while a new filter loads, so charts do not flash when cross-filtering.
 */
export function useTossInsightsQuery(query: InsightQuery | undefined) {
  return useQuery({
    queryKey: queryKeys.insights('toss', query ?? {}),
    queryFn: ({ signal }) => analyticsApi.tossInsights(query ?? {}, signal),
    enabled: query !== undefined,
    placeholderData: keepPreviousData,
  });
}

export function useBattingFirstInsightsQuery(query: InsightQuery | undefined) {
  return useQuery({
    queryKey: queryKeys.insights('batting-first', query ?? {}),
    queryFn: ({ signal }) => analyticsApi.battingFirstInsights(query ?? {}, signal),
    enabled: query !== undefined,
    placeholderData: keepPreviousData,
  });
}

export function usePhaseInsightsQuery(query: InsightQuery | undefined) {
  return useQuery({
    queryKey: queryKeys.insights('phases', query ?? {}),
    queryFn: ({ signal }) => analyticsApi.phaseInsights(query ?? {}, signal),
    enabled: query !== undefined,
    placeholderData: keepPreviousData,
  });
}

export function useSeasonComparisonQuery(query: SeasonComparisonQuery | undefined) {
  return useQuery({
    queryKey: queryKeys.insights('seasons', query ?? {}),
    queryFn: ({ signal }) => analyticsApi.seasonComparison(query ?? {}, signal),
    enabled: query !== undefined,
    placeholderData: keepPreviousData,
  });
}

export function usePlayerConsistencyQuery(id: number | undefined, query: PlayerStatsQuery | undefined) {
  return useQuery({
    queryKey: queryKeys.players.resource(id ?? 0, 'consistency', query ?? {}),
    queryFn: ({ signal }) => playersApi.consistency(id as number, query ?? {}, signal),
    enabled: id !== undefined && query !== undefined,
  });
}

export function useVenueTeamsQuery(id: number | undefined, query: VenueStatsQuery = {}) {
  return useQuery({
    queryKey: queryKeys.venues.resource(id ?? 0, 'teams', query),
    queryFn: ({ signal }) => venuesApi.teams(id as number, query, signal),
    enabled: id !== undefined,
  });
}
