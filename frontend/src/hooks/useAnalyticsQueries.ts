import { keepPreviousData, useQuery } from '@tanstack/react-query';
import {
  analyticsApi,
  type BattingLeaderboardQuery,
  type BowlingLeaderboardQuery,
  type ComparisonQuery,
  type HeadToHeadQuery,
  type MatchupQuery,
} from '@/api/analyticsApi';
import { queryKeys, STALE_TIME } from '@/lib/queryKeys';

export function useOverviewQuery() {
  return useQuery({
    queryKey: queryKeys.overview(),
    queryFn: ({ signal }) => analyticsApi.overview(signal),
    staleTime: STALE_TIME.reference,
  });
}

export function useBattingLeaderboardQuery(query: BattingLeaderboardQuery | undefined) {
  return useQuery({
    queryKey: queryKeys.leaderboards('batting', query ?? {}),
    queryFn: ({ signal }) => analyticsApi.battingLeaderboard(query ?? {}, signal),
    enabled: query !== undefined,
    placeholderData: keepPreviousData,
  });
}

export function useBowlingLeaderboardQuery(query: BowlingLeaderboardQuery | undefined) {
  return useQuery({
    queryKey: queryKeys.leaderboards('bowling', query ?? {}),
    queryFn: ({ signal }) => analyticsApi.bowlingLeaderboard(query ?? {}, signal),
    enabled: query !== undefined,
    placeholderData: keepPreviousData,
  });
}

/** Rivalry between two franchises; idle until both teams are chosen. */
export function useHeadToHeadQuery(query: HeadToHeadQuery | undefined) {
  return useQuery({
    queryKey: queryKeys.headToHead(query ?? {}),
    queryFn: ({ signal }) => analyticsApi.headToHead(query as HeadToHeadQuery, signal),
    enabled: query !== undefined,
  });
}

/** Side-by-side players; idle until at least two are chosen. */
export function usePlayerComparisonQuery(query: ComparisonQuery | undefined) {
  return useQuery({
    queryKey: queryKeys.players.compare(query ?? {}),
    queryFn: ({ signal }) => analyticsApi.compare(query as ComparisonQuery, signal),
    enabled: query !== undefined,
    placeholderData: keepPreviousData,
  });
}

/** Batter versus bowler; idle until both players are chosen. */
export function useMatchupQuery(query: MatchupQuery | undefined) {
  return useQuery({
    queryKey: queryKeys.matchup(query ?? {}),
    queryFn: ({ signal }) => analyticsApi.matchup(query as MatchupQuery, signal),
    enabled: query !== undefined,
  });
}
